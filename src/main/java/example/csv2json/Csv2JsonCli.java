package example.csv2json;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/**
 * CSVファイルをJSON形式に変換するためのコマンドラインインターフェースです。
 * 
 * <p>
 * このクラスはpicocliライブラリを使用してコマンドライン引数を解析し、
 * CsvToJsonCoreクラスを使用してCSVからJSONへの変換を実行します。
 * </p>
 * 
 * <p>
 * 主な機能：
 * </p>
 * <ul>
 * <li>CSVファイルの読み込み（標準入力も対応）</li>
 * <li>JSONファイルへの出力（標準出力も対応）</li>
 * <li>カスタマイズ可能な区切り文字、クォート文字、エスケープ文字</li>
 * <li>ヘッダー行の有無の設定</li>
 * <li>NDJSON形式での出力</li>
 * <li>整形されたJSON出力</li>
 * <li>文字エンコーディングの指定</li>
 * </ul>
 * 
 * <p>
 * 使用例：
 * </p>
 * 
 * <pre>{@code
 * # 基本的な変換
 * java -jar csv2json.jar -i input.csv -o output.json
 * 
 * # 標準入出力を使用
 * cat input.csv | java -jar csv2json.jar -i - -o -
 * 
 * # カスタム区切り文字とNDJSON出力
 * java -jar csv2json.jar -i data.tsv -o output.ndjson -d "\t" --ndjson
 * }</pre>
 * 
 * @author csv2json
 * @version 1.0
 * @since 1.0
 */
@Command(name = "csv2json", mixinStandardHelpOptions = true, version = "csv2json 0.1.0", description = "Convert CSV to JSON (array or NDJSON). Use '-' for stdin/stdout.")
public class Csv2JsonCli implements Callable<Integer> {

    /** 入力CSVファイルのパス（'-'の場合は標準入力） */
    @Option(names = { "-i", "--input" }, description = "Input CSV file path (use '-' for stdin).", required = true)
    String input;

    /** 出力JSONファイルのパス（'-'の場合は標準出力） */
    @Option(names = { "-o", "--output" }, description = "Output JSON file path (use '-' for stdout).", required = true)
    String output;

    /** CSVの区切り文字（デフォルト: カンマ） */
    @Option(names = { "-d", "--delimiter" }, description = "CSV delimiter char (default: ',')", defaultValue = ",")
    String delimiterStr;

    /** CSVのクォート文字（デフォルト: ダブルクォート） */
    @Option(names = { "-q", "--quote" }, description = "CSV quote char (default: '\"')", defaultValue = "\"")
    String quoteStr;

    /** CSVのエスケープ文字（デフォルト: バックスラッシュ） */
    @Option(names = { "-e", "--escape" }, description = "CSV escape char (default: '\\')", defaultValue = "\\")
    String escapeStr;

    /** ヘッダー行を無視して最初の行をデータとして扱うかどうか */
    @Option(names = { "--no-header" }, description = "Treat FIRST row as data (no header).")
    boolean noHeader;

    /** NDJSON形式（1行に1つのJSONオブジェクト）で出力するかどうか */
    @Option(names = { "--ndjson" }, description = "Write NDJSON (one JSON object per line), not a JSON array.")
    boolean ndjson;

    /** 整形されたJSONで出力するかどうか（NDJSONの場合は無視） */
    @Option(names = {
            "--pretty" }, description = "Pretty print JSON (default: true for array, false ignored for NDJSON).", defaultValue = "true")
    boolean pretty;

    /** 各セルの値をトリムしないかどうか（デフォルト: トリムする） */
    @Option(names = { "--no-trim" }, description = "Do not trim each cell (default: trim).")
    boolean noTrim;

    /** 空行をスキップしないかどうか（デフォルト: スキップする） */
    @Option(names = { "--keep-empty-lines" }, description = "Do not skip empty lines.")
    boolean keepEmpty;

    /** null値として扱う文字列（デフォルト: 空文字列で無効） */
    @Option(names = {
            "--null-token" }, description = "If a cell equals this token, it becomes JSON null (default: empty string disables).", defaultValue = "")
    String nullToken;

    /** 入出力の文字エンコーディング（デフォルト: UTF-8） */
    @Option(names = { "--charset" }, description = "Charset for input/output (default: UTF-8).", defaultValue = "UTF-8")
    String charset;

    /**
     * コマンドライン引数に基づいてCSVからJSONへの変換を実行します。
     * 
     * <p>
     * このメソッドはpicocliによって呼び出され、以下の処理を実行します：
     * </p>
     * <ol>
     * <li>コマンドライン引数をCsvToJsonCore.Optionsオブジェクトに変換</li>
     * <li>指定された文字エンコーディングでReaderとWriterを開く</li>
     * <li>CsvToJsonCore.convert()メソッドを呼び出して変換を実行</li>
     * <li>リソースを適切にクローズ</li>
     * </ol>
     * 
     * @return 実行結果（0: 成功、その他: エラー）
     * @throws Exception 変換処理中にエラーが発生した場合
     */
    @Override
    public Integer call() throws Exception {
        CsvToJsonCore.Options opt = new CsvToJsonCore.Options();
        opt.delimiter = getOneChar(delimiterStr, "delimiter");
        opt.quoteChar = getOneChar(quoteStr, "quote");
        opt.escapeChar = getOneChar(escapeStr, "escape");
        opt.hasHeader = !noHeader;
        opt.ndjson = ndjson;
        opt.pretty = pretty && !ndjson;
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

    /**
     * 文字列から1文字を取得し、バリデーションを行います。
     * 
     * <p>
     * 指定された文字列がnullまたは空文字列の場合、
     * ParameterExceptionをスローします。
     * </p>
     * 
     * @param s    文字列
     * @param name パラメータ名（エラーメッセージ用）
     * @return 文字列の最初の文字
     * @throws ParameterException 文字列がnullまたは空の場合
     */
    private char getOneChar(String s, String name) {
        if (s == null || s.isEmpty())
            throw new ParameterException(new CommandLine(this), name + " must be 1 char");
        return s.charAt(0);
    }

    /**
     * アプリケーションのエントリーポイントです。
     * 
     * <p>
     * コマンドライン引数を解析し、Csv2JsonCliインスタンスを実行します。
     * </p>
     * 
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        int code = new CommandLine(new Csv2JsonCli()).execute(args);
        System.exit(code);
    }
}
