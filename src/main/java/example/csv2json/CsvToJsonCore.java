package example.csv2json;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

public class CsvToJsonCore {

    public static class Options {
        public char delimiter = ',';
        public char quoteChar = '"';
        public char escapeChar = '\\';
        public boolean hasHeader = true;
        public boolean ndjson = false;
        public boolean pretty = true;
        public boolean trim = true;
        public boolean skipEmptyLines = true;
        public String nullValue = "";
    }

    public static void convert(Reader in, Writer out, Options opt) throws IOException {
        CSVReader reader = new CSVReaderBuilder(in)
                .withCSVParser(new CSVParserBuilder()
                        .withSeparator(opt.delimiter)
                        .withQuoteChar(opt.quoteChar)
                        .withEscapeChar(opt.escapeChar)
                        .build())
                .build();

        ObjectMapper mapper = new ObjectMapper();
        if (opt.pretty && !opt.ndjson) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }

        String[] headers = null;
        List<Map<String, Object>> rows = opt.ndjson ? null : new ArrayList<>();

        try {
            if (opt.hasHeader) {
                headers = reader.readNext();
                if (headers == null)
                    headers = new String[0];
                if (opt.trim)
                    trimArray(headers);
            }

            String[] line;
            while ((line = reader.readNext()) != null) {
                if (opt.trim)
                    trimArray(line);
                if (opt.skipEmptyLines && isAllEmpty(line))
                    continue;

                if (!opt.hasHeader && headers == null) {
                    headers = genDefaultHeaders(line.length);
                }

                Map<String, Object> obj = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < line.length; i++) {
                    String v = line[i];
                    Object value = toValue(v, opt.nullValue);
                    obj.put(headers[i], value);
                }

                if (opt.ndjson) {
                    mapper.writeValue(out, obj);
                    out.write(System.lineSeparator());
                } else {
                    rows.add(obj);
                }
            }
        } catch (Exception e) {
            throw new IOException("CSV parsing error: " + e.getMessage(), e);
        } finally {
            try {
                reader.close();
            } catch (Exception ignore) {
            }
        }

        if (!opt.ndjson) {
            mapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
            mapper.writeValue(out, rows);
        }
        out.flush();
    }

    private static String[] genDefaultHeaders(int n) {
        String[] h = new String[n];
        for (int i = 0; i < n; i++)
            h[i] = "column_" + (i + 1);
        return h;
    }

    private static void trimArray(String[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (arr[i] == null) ? null : arr[i].trim();
        }
    }

    private static boolean isAllEmpty(String[] arr) {
        for (String s : arr) {
            if (s != null && !s.isBlank())
                return false;
        }
        return true;
    }

    private static Object toValue(String s, String nullToken) {
        if (s == null)
            return null;
        if (!nullToken.isEmpty() && s.equals(nullToken))
            return null;
        try {
            if (s.matches("^-?\\d+$"))
                return Long.parseLong(s);
            if (s.matches("^-?\\d*\\.\\d+$"))
                return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
        }
        return s;
    }

    public static Reader openReader(String path, Charset cs) throws IOException {
        if ("-".equals(path))
            return new InputStreamReader(System.in, cs);
        return new InputStreamReader(new FileInputStream(path), cs);
    }

    public static Writer openWriter(String path, Charset cs) throws IOException {
        if ("-".equals(path))
            return new OutputStreamWriter(System.out, cs);
        return new OutputStreamWriter(new FileOutputStream(path), cs);
    }
}
