package com.corpusai.rag;

//The subject context handed to a generator
public record RetrievedContent(String text, int chunkCount) {}
