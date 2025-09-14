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

/**
 * CSVファイルをJSON形式に変換するためのコアクラスです。
 * 
 * <p>
 * このクラスは以下の機能を提供します：
 * </p>
 * <ul>
 * <li>CSVファイルの読み込みとパース</li>
 * <li>JSON配列またはNDJSON（改行区切りJSON）形式での出力</li>
 * <li>カスタマイズ可能な区切り文字、クォート文字、エスケープ文字の設定</li>
 * <li>ヘッダー行の有無の設定</li>
 * <li>空行のスキップ機能</li>
 * <li>数値の自動型変換</li>
 * </ul>
 * 
 * <p>
 * 使用例：
 * </p>
 * 
 * <pre>{@code
 * CsvToJsonCore.Options options = new CsvToJsonCore.Options();
 * options.delimiter = ',';
 * options.hasHeader = true;
 * 
 * try (Reader reader = new FileReader("input.csv");
 *         Writer writer = new FileWriter("output.json")) {
 *     CsvToJsonCore.convert(reader, writer, options);
 * }
 * }</pre>
 * 
 * @author csv2json
 * @version 1.0
 * @since 1.0
 */
public class CsvToJsonCore {

    /**
     * CSVからJSONへの変換オプションを設定するためのクラスです。
     * 
     * <p>
     * このクラスは変換処理で使用される各種パラメータを保持します。
     * </p>
     * 
     * @author csv2json
     * @version 1.0
     * @since 1.0
     */
    public static class Options {
        /** CSVの区切り文字（デフォルト: カンマ） */
        public char delimiter = ',';
        /** CSVのクォート文字（デフォルト: ダブルクォート） */
        public char quoteChar = '"';
        /** CSVのエスケープ文字（デフォルト: バックスラッシュ） */
        public char escapeChar = '\\';
        /** ヘッダー行が存在するかどうか（デフォルト: true） */
        public boolean hasHeader = true;
        /** NDJSON形式で出力するかどうか（デフォルト: false） */
        public boolean ndjson = false;
        /** 整形されたJSONで出力するかどうか（デフォルト: true） */
        public boolean pretty = true;
        /** 各セルの値をトリムするかどうか（デフォルト: true） */
        public boolean trim = true;
        /** 空行をスキップするかどうか（デフォルト: true） */
        public boolean skipEmptyLines = true;
        /** null値として扱う文字列（デフォルト: 空文字列） */
        public String nullValue = "";
    }

    /**
     * CSVデータをJSON形式に変換します。
     * 
     * <p>
     * このメソッドは指定されたReaderからCSVデータを読み込み、
     * 指定されたWriterにJSON形式で出力します。
     * </p>
     * 
     * <p>
     * 変換処理の詳細：
     * </p>
     * <ul>
     * <li>ヘッダー行が存在する場合、最初の行をヘッダーとして使用</li>
     * <li>ヘッダー行が存在しない場合、自動的に「column_1」「column_2」...を生成</li>
     * <li>数値文字列は自動的にLongまたはDoubleに変換</li>
     * <li>空行は設定に応じてスキップ</li>
     * <li>各セルの値は設定に応じてトリム</li>
     * </ul>
     * 
     * @param in  CSVデータを読み込むReader
     * @param out JSONデータを出力するWriter
     * @param opt 変換オプション
     * @throws IOException              CSVの読み込みまたはJSONの書き込みでエラーが発生した場合
     * @throws IllegalArgumentException 無効なオプションが指定された場合
     */
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

    /**
     * デフォルトのヘッダー名を生成します。
     * 
     * <p>
     * ヘッダー行が存在しない場合に使用されるヘッダー名を生成します。
     * 生成されるヘッダー名は「column_1」「column_2」...の形式になります。
     * </p>
     * 
     * @param n 生成するヘッダー名の数
     * @return 生成されたヘッダー名の配列
     */
    private static String[] genDefaultHeaders(int n) {
        String[] h = new String[n];
        for (int i = 0; i < n; i++)
            h[i] = "column_" + (i + 1);
        return h;
    }

    /**
     * 文字列配列の各要素をトリムします。
     * 
     * <p>
     * 配列内の各文字列要素の前後の空白文字を削除します。
     * null要素はそのまま保持されます。
     * </p>
     * 
     * @param arr トリムする文字列配列（この配列が直接変更されます）
     */
    private static void trimArray(String[] arr) {
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (arr[i] == null) ? null : arr[i].trim();
        }
    }

    /**
     * 文字列配列のすべての要素が空かどうかを判定します。
     * 
     * <p>
     * 配列内のすべての要素がnullまたは空白文字のみの場合にtrueを返します。
     * </p>
     * 
     * @param arr 判定する文字列配列
     * @return すべての要素が空の場合はtrue、そうでなければfalse
     */
    private static boolean isAllEmpty(String[] arr) {
        for (String s : arr) {
            if (s != null && !s.isBlank())
                return false;
        }
        return true;
    }

    /**
     * 文字列を適切な型の値に変換します。
     * 
     * <p>
     * 文字列の内容に基づいて以下の型に自動変換します：
     * </p>
     * <ul>
     * <li>整数形式の文字列 → Long</li>
     * <li>小数形式の文字列 → Double</li>
     * <li>nullTokenと一致する文字列 → null</li>
     * <li>その他の文字列 → そのままの文字列</li>
     * </ul>
     * 
     * @param s         変換する文字列
     * @param nullToken null値として扱う文字列
     * @return 変換された値（Long、Double、String、またはnull）
     */
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

    /**
     * 指定されたパスからReaderを開きます。
     * 
     * <p>
     * パスが「-」の場合は標準入力から読み込みます。
     * それ以外の場合は指定されたファイルから読み込みます。
     * </p>
     * 
     * @param path 入力ファイルのパス（「-」の場合は標準入力）
     * @param cs   文字エンコーディング
     * @return 開かれたReader
     * @throws IOException ファイルの読み込みでエラーが発生した場合
     */
    public static Reader openReader(String path, Charset cs) throws IOException {
        if ("-".equals(path))
            return new InputStreamReader(System.in, cs);
        return new InputStreamReader(new FileInputStream(path), cs);
    }

    /**
     * 指定されたパスにWriterを開きます。
     * 
     * <p>
     * パスが「-」の場合は標準出力に書き込みます。
     * それ以外の場合は指定されたファイルに書き込みます。
     * </p>
     * 
     * @param path 出力ファイルのパス（「-」の場合は標準出力）
     * @param cs   文字エンコーディング
     * @return 開かれたWriter
     * @throws IOException ファイルの書き込みでエラーが発生した場合
     */
    public static Writer openWriter(String path, Charset cs) throws IOException {
        if ("-".equals(path))
            return new OutputStreamWriter(System.out, cs);
        return new OutputStreamWriter(new FileOutputStream(path), cs);
    }
}
