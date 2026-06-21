package com.example

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mocked wrapper for the native Llama.cpp engine.
 * Replace with actual JNI calls or bindings when integrated.
 */
object LlamaCpp {
    
    fun initContext(
        modelPath: String,
        nCtx: Int,
        nThreads: Int,
        useMmap: Boolean
    ) {
        // TODO: Map to native JNI Context Init:
        // NativeBindings.init(modelPath, nCtx, nThreads, useMmap)
        println("LlamaCpp Context Initialized with $modelPath")
    }
    
    fun completion(
        prompt: String,
        nPredict: Int,
        temperature: Double,
        topK: Int,
        topP: Double
    ): Flow<String> = flow {
        // TODO: Replace with real callback stream from native JNI inference.
        // e.g., NativeBindings.startCompletion(...) { token -> emit(token) }
        val fakeWordsToStream = listOf(
            "Hello,", " this", " is", " a", " test", " response", " from", 
            " the", " Sednium", " native", " engine! \n\n",
            "This", " confirms", " the", " UI", " is", " streaming", " properly."
        )
        for (token in fakeWordsToStream) {
            delay(100) // fake generation latency
            emit(token)
        }
    }
}
