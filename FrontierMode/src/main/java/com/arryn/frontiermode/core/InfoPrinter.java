package com.arryn.frontiermode.core;

import com.arryn.satchel.Satchel;
import com.arryn.satchel.common.util.out.OUT;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Posts an {@link InfoRequest} on Satchel's bus, then prints what the modules added: the strings grouped
 * under their subject in the poster's order, each in the order it arrived, each as given. The sink is
 * where the finished strings go (the holder's chat for the Inspect Stick); the whole report also goes to
 * the server log on one line, with newlines turned into separators.
 *
 * <p><b>The Info report never crashes the server.</b> This is a named exception to the deferred rescue
 * policy, for this one report: a debug report is not worth a crashed world. Each string is evaluated in
 * its own try/catch, so one that throws is logged at error, echoed to the sink as an error line naming
 * its source, and skipped. A consumer that throws while adding ends the post; what was added still
 * prints, followed by an error line.
 */
public final class InfoPrinter {

    private InfoPrinter() {
    }

    public static void report(InfoRequest request, Consumer<String> sink) {
        String postError = null;
        try {
            Satchel.require().eventBus().post(request);
        } catch (RuntimeException e) {
            OUT.error("[Info] a module failed while adding to the report", e);
            postError = "[Info] error: a module failed while adding to the report: " + e;
        }

        List<String> logLines = new ArrayList<>();
        for (Object subject : request.subjects()) {
            for (InfoRequest.Entry entry : request.entriesFor(subject)) {
                String text;
                try {
                    text = entry.text().get();
                } catch (RuntimeException e) {
                    OUT.error("[Info] " + entry.source() + " failed to build its line", e);
                    sink.accept("[Info] error: " + entry.source() + " failed to build its line: " + e);
                    continue;
                }
                if (text == null || text.isEmpty()) {
                    continue;
                }
                sink.accept(text);
                logLines.addAll(List.of(text.split("\n")));
            }
        }
        if (postError != null) {
            sink.accept(postError);
        }

        OUT.info("[Info] report(): " + String.join(" / ", logLines));
    }
}
