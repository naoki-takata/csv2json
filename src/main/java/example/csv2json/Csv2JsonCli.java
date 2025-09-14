package main.java.example.csv2json;

import info.picocli.CommandLine;
import info.picocli.CommandLine.*;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;

@Command(name = "csv2json", mixinStandardHelpOptions = true, version = "csv2json 0.1.0", description = "Convert CSV to JSON (array or NDJSON). Use '-' for stdin/stdout.")
public class Csv2JsonCli implements Callable<Integer> {

    @Option(names = { "-i", "--input" }, description = "Input CSV file path (use '-' for stdin).", required = true)
    String input;

    @Option(names = { "-o", "--output" }, description = "Output JSON file path (use '-' for stdout).", required = true)
    String output;

    @Option(names = { "-d", "--delimiter" }, description = "CSV delimiter char (default: ',')", defaultValue = ",")
    String delimiterStr;

    @Option(names = { "-q", "--quote" }, description = "CSV quote char (default: '\"')", defaultValue = "\"")
    String quoteStr;

    @Option(names = { "-e", "--escape" }, description = "CSV escape char (default: '\\')", defaultValue = "\\")
    String escapeStr;

    @Option(names = { "--no-header" }, description = "Treat FIRST row as data (no header).")
    boolean noHeader;

    @Option(names = { "--ndjson" }, description = "Write NDJSON (one JSON object per line), not a JSON array.")
    boolean ndjson;

    @Option(names = {
            "--pretty" }, description = "Pretty print JSON (default: true for array, false ignored for NDJSON).", defaultValue = "true")
    boolean pretty;

    @Option(names = { "--no-trim" }, description = "Do not trim each cell (default: trim).")
    boolean noTrim;

    @Option(names = { "--keep-empty-lines" }, description = "Do not skip empty lines.")
    boolean keepEmpty;

    @Option(names = {
            "--null-token" }, description = "If a cell equals this token, it becomes JSON null (default: empty string disables).", defaultValue = "")
    String nullToken;

    @Option(names = { "--charset" }, description = "Charset for input/output (default: UTF-8).", defaultValue = "UTF-8")
    String charset;

    @Override
    public Integer call() throws Exception {
        CsvToJsonCore.Options opt = new CsvToJsonCore.Options();
        opt.delimiter = getOneChar(delimiterStr, "delimiter");
        opt.quoteChar = getOneChar(quoteStr, "quote");
        opt.escapeChar = getOneChar(escapeStr, "escape");
        opt.hasHeader = !noHeader;
        opt.ndjson = ndjson;
        opt.pretty = pretty && !ndjson; // NDJSONは整形しない
        opt.trim = !noTrim;
        opt.skipEmptyLines = !keepEmpty;
        opt.nullValue = nullToken;

        Charset cs = Charset.forName(charset);

        try (Reader in = CsvToJsonCore.openReader(input, cs);
                Writer out = CsvToJsonCore.openWriter(output, cs)) {
            CsvToJsonCore.convert(in, out, opt);
        }
        return 0;
    }

    private char getOneChar(String s, String name) {
        if (s == null || s.isEmpty())
            throw new ParameterException(new CommandLine(this), name + " must be 1 char");
        return s.charAt(0);
    }

    public static void main(String[] args) {
        int code = new CommandLine(new Csv2JsonCli()).execute(args);
        System.exit(code);
    }
}
