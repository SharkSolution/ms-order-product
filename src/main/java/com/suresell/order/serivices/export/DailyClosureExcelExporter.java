/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.suresell.order.model.record.ClosureResponse
 *  com.suresell.order.serivices.export.DailyClosureExcelExporter
 *  org.apache.poi.xssf.usermodel.XSSFWorkbook
 *  org.springframework.stereotype.Service
 */
package com.suresell.order.serivices.export;

import com.suresell.order.model.record.ClosureResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class DailyClosureExcelExporter {
    public ByteArrayInputStream export(List<ClosureResponse> closures) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();){
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write((OutputStream)out);
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(out.toByteArray());
            return byteArrayInputStream;
        }
    }
}

