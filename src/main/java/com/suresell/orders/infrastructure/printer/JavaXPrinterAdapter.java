package com.suresell.orders.infrastructure.printer;
import com.suresell.orders.domain.port.out.PrinterPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import java.util.Arrays;
@Service
public class JavaXPrinterAdapter implements PrinterPort {
    @Value("${printer.name:SAT}")  
    private String printerName;
    private final EscPosBuilder escPosBuilder;
    public JavaXPrinterAdapter(EscPosBuilder escPosBuilder) {
        this.escPosBuilder = escPosBuilder;
    }
    @Override
    public void printBytes(byte[] data) {
        PrintService service = findPrintService(printerName);
        if (service == null) throw new RuntimeException("Impresora no encontrada: " + printerName);
        try {
            DocPrintJob job = service.createPrintJob();
            Doc doc = new SimpleDoc(data, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            job.print(doc, attributes);
        } catch (PrintException e) {
            throw new RuntimeException("Error enviando datos a la impresora", e);
        }
    }
    @Override
    public void openDrawer() {
        printBytes(escPosBuilder.getOpenDrawerCommand());
    }
    @Override
    public boolean isPrinterReady() {
        return findPrintService(printerName) != null;
    }
    private PrintService findPrintService(String nameFragment) {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        return Arrays.stream(services)
                .filter(s -> s.getName().equalsIgnoreCase(nameFragment))
                .findFirst()
                .orElse(Arrays.stream(services)
                        .filter(s -> s.getName().toLowerCase().contains(nameFragment.toLowerCase()))
                        .findFirst()
                        .orElse(PrintServiceLookup.lookupDefaultPrintService()));
    }
}