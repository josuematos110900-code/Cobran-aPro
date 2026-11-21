package com.pastelgest.verify;

import java.util.ArrayList;
import java.util.List;

/** Mini framework de verificação com relatório legível. */
public final class Check {
    static final List<String> passed = new ArrayList<>();
    static final List<String> failed = new ArrayList<>();

    interface Body { void run() throws Exception; }

    static void eq(String what, long expected, long actual) {
        if (expected == actual) passed.add(what + " = " + actual);
        else failed.add(what + ": esperado " + expected + ", obtido " + actual);
    }

    static void eqs(String what, String expected, String actual) {
        if (expected == null ? actual == null : expected.equals(actual)) passed.add(what + " = " + actual);
        else failed.add(what + ": esperado " + expected + ", obtido " + actual);
    }

    static void ok(String what, boolean cond) {
        if (cond) passed.add(what);
        else failed.add(what + ": FALHOU");
    }

    /** A operação tem de falhar com BusinessException contendo o texto. */
    static void fails(String what, String contains, Body b) {
        try {
            b.run();
            failed.add(what + ": deveria ter falhado");
        } catch (com.pastelgest.app.core.BusinessException e) {
            if (contains == null || e.getMessage().contains(contains)) passed.add(what + " → \"" + e.getMessage().split("\n")[0] + "\"");
            else failed.add(what + ": mensagem inesperada: " + e.getMessage());
        } catch (Exception e) {
            failed.add(what + ": excepção inesperada " + e);
        }
    }

    static int report(String title) {
        System.out.println("\n==== " + title + " ====");
        for (String p : passed) System.out.println("  [OK]    " + p);
        for (String f : failed) System.out.println("  [FALHA] " + f);
        System.out.println("  Total: " + passed.size() + " OK, " + failed.size() + " falhas");
        int n = failed.size();
        passed.clear();
        failed.clear();
        return n;
    }
}
