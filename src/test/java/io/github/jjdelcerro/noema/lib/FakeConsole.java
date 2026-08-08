package io.github.jjdelcerro.noema.lib;

import io.github.jjdelcerro.noema.lib.AgentConsole;

public class FakeConsole implements AgentConsole {

    @Override public boolean confirm(String message) { return true; } // Por defecto auto-aprueba
    @Override public void printSystemError(String message) { System.err.println("[ERR] " + message); }
    @Override public void printSystemLog(String message) { System.out.println("[LOG] " + message); }
    @Override public void printSystemLog(String message, Format format) { System.out.println("[LOG] " + message); }
    @Override public void printUserMessage(String message) { System.out.println("USER > " + message); }
    @Override public void printModelResponse(String message) { System.out.println("MODEL > " + message); }
    @Override public void printModelReasoning(String message) { System.out.println("REASONING > " + message); }
}