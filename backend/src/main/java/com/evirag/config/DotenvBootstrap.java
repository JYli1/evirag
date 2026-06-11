package com.evirag.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * EviRAG 的 dotenv 启动加载器。
 *
 * <p>Spring Boot 默认不会自动读取任意位置的 {@code .env} 文件，而本项目约定所有运行配置集中放在
 * {@code backend/.env}。因此必须在 {@code SpringApplication.run(...)} 之前执行本类，把 .env 中的键值加载为
 * JVM 系统属性，随后 {@code application.yml} 里的 {@code ${KEY:default}} 占位符才能被 Spring 正常解析。</p>
 *
 * <p>加载规则保持克制：只支持常见的 {@code KEY=value} 行、空行和 {@code #} 注释行；不会把真实密钥写入仓库。</p>
 *
 * <p>配置优先级必须明确：JVM 系统属性（例如 {@code -DDB_PASSWORD=...}）最高，其次是宿主机 OS 环境变量，
 * 最后才是本地 {@code backend/.env} 回退值。这样 CI 或生产环境注入的数据库密码、JWT 密钥和模型 API Key
 * 不会被开发机残留的 .env 覆盖。</p>
 */
public final class DotenvBootstrap {

    /**
     * 测试或特殊启动场景可通过该系统属性显式指定 .env 路径。
     */
    public static final String DOTENV_PATH_PROPERTY = "evirag.dotenv.path";

    private DotenvBootstrap() {
        // 工具类不需要实例化，避免在业务代码中误创建对象。
    }

    /**
     * 从默认位置加载 .env。
     *
     * <p>当进程工作目录是 {@code backend} 时读取 {@code .env}；当进程从仓库根目录启动时读取
     * {@code backend/.env}。如果两个位置都不存在，则静默跳过，继续使用系统环境变量或默认值。</p>
     */
    public static void load() {
        resolveDotenvPath().ifPresent(DotenvBootstrap::load);
    }

    /**
     * 从指定路径加载 .env，主要供启动入口和测试复用。
     *
     * @param dotenvPath .env 文件路径
     */
    public static void load(Path dotenvPath) {
        load(dotenvPath, System::getenv);
    }

    /**
     * 从指定路径加载 .env，并允许测试传入可控的宿主环境读取器。
     *
     * <p>Java 标准库不允许安全地修改 {@code System.getenv()}，测试不应使用反射破坏进程环境。因此这里把环境读取
     * 抽成函数参数：生产路径传入 {@code System::getenv}，测试路径传入假的环境值，用同一条分支验证不覆盖 OS 环境变量。</p>
     *
     * @param dotenvPath .env 文件路径
     * @param environmentReader 宿主机环境变量读取函数
     */
    static void load(Path dotenvPath, Function<String, String> environmentReader) {
        if (dotenvPath == null || !Files.isRegularFile(dotenvPath)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(dotenvPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                // 每一行都独立解析；坏格式会被跳过，不会影响后面的有效配置。
                applyLine(line, environmentReader);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("读取 EviRAG .env 配置失败：" + dotenvPath, ex);
        }
    }

    /**
     * 解析并应用单行 KEY=value 配置。
     *
     * <p>这里刻意不做复杂 shell 语法兼容，避免把 .env 解析成隐式脚本；后续配置需要更复杂格式时，应优先拆成明确键值。</p>
     */
    private static void applyLine(String rawLine, Function<String, String> environmentReader) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (line.isEmpty() || line.startsWith("#")) {
            return;
        }

        int separatorIndex = line.indexOf('=');
        if (separatorIndex <= 0) {
            return;
        }

        String key = line.substring(0, separatorIndex).trim();
        String value = stripWrappingQuotes(line.substring(separatorIndex + 1).trim());
        if (key.isEmpty() || hasExistingValue(key, environmentReader)) {
            return;
        }

        // 写入 System property 后，application.yml 中的 ${KEY:default} 就能读取到该值。
        System.setProperty(key, value);
    }

    /**
     * 判断 JVM 或宿主机是否已经提供该配置。
     *
     * <p>这里不能只检查 {@code System.getProperty(key)}：Spring 会优先读取系统属性，如果 .env 被写成系统属性，
     * 就会压过真实 OS 环境变量。只有两者都缺失时，本地 .env 才能作为开发回退值写入。</p>
     */
    private static boolean hasExistingValue(String key, Function<String, String> environmentReader) {
        if (System.getProperty(key) != null) {
            return true;
        }
        return environmentReader != null && environmentReader.apply(key) != null;
    }

    /**
     * 去掉一层成对的单引号或双引号，便于在 .env 中书写包含空格或等号的值。
     */
    private static String stripWrappingQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }

        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * 按优先级寻找 .env 文件路径。
     */
    private static Optional<Path> resolveDotenvPath() {
        String explicitPath = System.getProperty(DOTENV_PATH_PROPERTY);
        if (explicitPath != null && !explicitPath.isBlank()) {
            // 测试或 IDEA 特殊运行配置可以通过 -Devirag.dotenv.path=... 指定实际 .env。
            return Optional.of(Path.of(explicitPath));
        }

        Path backendWorkingDirectoryPath = Path.of(".env");
        if (Files.isRegularFile(backendWorkingDirectoryPath)) {
            return Optional.of(backendWorkingDirectoryPath);
        }

        Path repositoryRootPath = Path.of("backend", ".env");
        if (Files.isRegularFile(repositoryRootPath)) {
            return Optional.of(repositoryRootPath);
        }

        return Optional.empty();
    }
}
