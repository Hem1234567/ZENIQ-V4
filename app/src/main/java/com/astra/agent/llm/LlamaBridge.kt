package com.astra.agent.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun interface TokenCallback { fun onToken(bytes: ByteArray): Boolean }

object LlamaBridge {
    init { System.loadLibrary("astra_llama") }
    external fun nativeLoad(path: String, nCtx: Int, threads: Int): Boolean
    external fun nativeGenerate(prompt: ByteArray, maxTokens: Int, temp: Float, topP: Float, cb: TokenCallback)
    external fun nativeWarmup(prompt: ByteArray)
    external fun nativeStop()
    external fun nativeFree()
}

/** Thin safe wrapper — all native calls are serialised through this object. */
object LlmEngine {
    @Volatile var loadedName: String? = null
    @Volatile private var loading = false
    /** The native context can only do one thing at a time: load, warm-up and generate all go through this lock. */
    private val gate = java.util.concurrent.locks.ReentrantLock()
    @Volatile private var warming = false

    /**
     * Loads a model. Automatically unloads any previously loaded model first.
     * Returns false if: already loading, OOM, native error, or file missing.
     */
    suspend fun load(file: File, nCtx: Int, threads: Int): Boolean = withContext(Dispatchers.Default) {
        if (loading) return@withContext false
        if (!file.exists() || file.length() == 0L) return@withContext false
        loading = true
        try {
            // Free current model first to release RAM before loading the new one
            gate.lock()
            if (loadedName != null) {
                try { LlamaBridge.nativeFree() } catch (_: Throwable) {}
                loadedName = null
            }
            // Check available heap before attempting load
            val runtime = Runtime.getRuntime()
            val freeBytes = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
            if (freeBytes < 100_000_000L) {
                // Request GC to free any reachable garbage
                System.gc()
            }
            val ok = try {
                LlamaBridge.nativeLoad(file.absolutePath, nCtx.coerceIn(512, 8192), threads.coerceIn(1, 8))
            } catch (e: OutOfMemoryError) {
                false
            } catch (e: Throwable) {
                false
            }
            if (ok) loadedName = file.name
            ok
        } finally {
            if (gate.isHeldByCurrentThread) gate.unlock()
            loading = false
        }
    }

    /** Blocking text generation. Call from Dispatchers.Default only. */
    fun generate(prompt: String, maxTokens: Int, temp: Float, topP: Float, onText: (String) -> Boolean) {
        if (loadedName == null) return
        if (warming) stop()                       // a question is more important than the warm-up: cut it short
        gate.lock()
        // The model threads inherit this thread's priority. A slightly lower priority lets the screen always win the CPU,
        // so the app keeps responding while the model is thinking.
        val oldPrio = try { android.os.Process.getThreadPriority(android.os.Process.myTid()) } catch (_: Throwable) { 0 }
        try { android.os.Process.setThreadPriority(4) } catch (_: Throwable) {}
        try {
            if (loadedName == null) return
            LlamaBridge.nativeGenerate(
                prompt.toByteArray(Charsets.UTF_8),
                maxTokens.coerceIn(64, 4096),
                temp.coerceIn(0f, 2f),
                topP.coerceIn(0f, 1f)
            ) { bytes -> onText(String(bytes, Charsets.UTF_8)) }
        } catch (e: OutOfMemoryError) {
            onText("\n[Out of memory — please load a smaller model]"); onText(""); false
        } catch (_: Throwable) {
        } finally {
            try { android.os.Process.setThreadPriority(oldPrio) } catch (_: Throwable) {}
            gate.unlock()
        }
    }

    /** Pre-reads the fixed start of the chat prompt so the next answer starts sooner. Safe to fail. */
    suspend fun warmup(prefix: String) = withContext(Dispatchers.Default) {
        if (loadedName == null) return@withContext
        if (!gate.tryLock()) return@withContext   // busy loading or answering: skip, it is only an optimisation
        warming = true
        val oldPrio = try { android.os.Process.getThreadPriority(android.os.Process.myTid()) } catch (_: Throwable) { 0 }
        try { android.os.Process.setThreadPriority(8) } catch (_: Throwable) {}
        try { if (loadedName != null) LlamaBridge.nativeWarmup(prefix.toByteArray(Charsets.UTF_8)) }
        catch (_: Throwable) {}
        finally { try { android.os.Process.setThreadPriority(oldPrio) } catch (_: Throwable) {}; warming = false; gate.unlock() }
    }

    fun stop() { try { LlamaBridge.nativeStop() } catch (_: Throwable) {} }

    fun unload() {
        gate.lock()
        try { if (loadedName != null) LlamaBridge.nativeFree() } catch (_: Throwable) {}
        finally { loadedName = null; gate.unlock() }
    }
}
