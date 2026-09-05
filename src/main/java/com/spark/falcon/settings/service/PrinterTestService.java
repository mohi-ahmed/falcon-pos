package com.spark.falcon.settings.service;

import com.spark.falcon.settings.entity.Printer;
import com.spark.falcon.settings.entity.enumtype.PrinterConnectionType;
import org.springframework.stereotype.Service;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class PrinterTestService {
    private static final byte[] TEST_RECEIPT = "Falcon POS\nTest print successful\n\n".getBytes(StandardCharsets.UTF_8);

    public void test(Printer printer) {
        print(printer, TEST_RECEIPT, "Test print failed");
    }

    public void print(Printer printer, byte[] content) {
        print(printer, content, "Receipt print failed");
    }

    private void print(Printer printer, byte[] content, String failureMessage) {
        try {
            if (printer.getConnectionType() == PrinterConnectionType.NETWORK) printNetwork(printer, content);
            else printSystemPrinter(printer, content);
        } catch (Exception exception) {
            throw new IllegalStateException(failureMessage + ": " + exception.getMessage(), exception);
        }
    }

    private void printNetwork(Printer printer, byte[] content) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printer.getIpAddress(), printer.getPort()),
                    (int) Duration.ofSeconds(5).toMillis());
            try (OutputStream output = socket.getOutputStream()) {
                output.write(content);
                output.flush();
            }
        }
    }

    private void printSystemPrinter(Printer printer, byte[] content) throws Exception {
        PrintService selected = null;
        for (PrintService candidate : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (candidate.getName().equalsIgnoreCase(printer.getPrinterPath())) {
                selected = candidate;
                break;
            }
        }
        if (selected == null) throw new IllegalStateException("Configured system printer was not found");
        DocPrintJob job = selected.createPrintJob();
        Doc document = new SimpleDoc(content, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        job.print(document, null);
    }
}
