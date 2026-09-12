package com.ai.config.redis;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 探测 Redis 是否可用，并写入 Session / 自动配置相关属性。
 */
public final class RedisAvailabilityConfigurer {

    public static final String PROPERTY = "app.redis.available";
    private static final String MODE_PROPERTY = "app.redis.mode";
    private static final String REDIS_AUTO_CONFIG =
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration";
    private static final String REDIS_REACTIVE_AUTO_CONFIG =
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration";
    private static final String SESSION_AUTO_CONFIG =
            "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration";

    private RedisAvailabilityConfigurer() {
    }

    public static void apply(ConfigurableEnvironment environment) {
        String mode = environment.getProperty(MODE_PROPERTY, "auto").trim().toLowerCase();
        boolean available = resolveAvailability(environment, mode);

        Map<String, Object> props = new HashMap<>();
        props.put(PROPERTY, Boolean.toString(available));
        props.put("spring.session.store-type", available ? "redis" : "none");

        if (!available) {
            mergeAutoConfigureExclude(environment, props, REDIS_AUTO_CONFIG);
            mergeAutoConfigureExclude(environment, props, REDIS_REACTIVE_AUTO_CONFIG);
            mergeAutoConfigureExclude(environment, props, SESSION_AUTO_CONFIG);
            // langchain4j-community-redis-starter 有独立自动装配，不走 RedisAutoConfiguration，
            // 仍会注册连接工厂 → actuator redis 指示器存在且必然 DOWN，拖垮 /actuator/health。
            // Redis 既然有意关闭，指示器一并停用。
            props.put("management.health.redis.enabled", Boolean.FALSE.toString());
        }

        environment.getPropertySources().addFirst(new MapPropertySource("redisAvailability", props));
        System.out.println("[Redis] mode=" + mode + ", available=" + available
                + ", session=" + (available ? "redis" : "servlet(in-memory)"));
    }

    private static void mergeAutoConfigureExclude(ConfigurableEnvironment environment,
                                                  Map<String, Object> props,
                                                  String excludeClass) {
        String existing = environment.getProperty("spring.autoconfigure.exclude", "");
        if (existing.isBlank()) {
            props.put("spring.autoconfigure.exclude", excludeClass);
        } else if (!existing.contains(excludeClass)) {
            props.put("spring.autoconfigure.exclude", existing + "," + excludeClass);
        }
    }

    private static boolean resolveAvailability(ConfigurableEnvironment environment, String mode) {
        return switch (mode) {
            case "on", "true", "required" -> true;
            case "off", "false", "disabled" -> false;
            default -> pingRedis(environment);
        };
    }

    /**
     * 真正对 Redis 发送 RESP {@code PING} 并要求回复 {@code +PONG}。
     * 只做 TCP connect 无法区分「真 Redis」与「仅接受连接但不应答的黑洞端口」
     * （如被 VMware NAT / 其它服务占用的端口），后者会导致运行期每个请求在获取
     * Redis 连接时无限阻塞。这里通过读到 +PONG 才判定可用，从根源避免该问题。
     */
    private static boolean pingRedis(ConfigurableEnvironment environment) {
        String host = environment.getProperty("spring.data.redis.host", "127.0.0.1");
        int port = environment.getProperty("spring.data.redis.port", Integer.class, 6379);
        String password = environment.getProperty("spring.data.redis.password", "");
        int timeoutMs = environment.getProperty("app.redis.ping-timeout-ms", Integer.class, 2000);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            if (password != null && !password.isBlank()) {
                out.write(("AUTH " + password + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                readReply(in);
            }

            out.write("PING\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            String reply = readReply(in);
            return reply.startsWith("+PONG");
        } catch (IOException e) {
            return false;
        }
    }

    /** 读取一行 RESP 回复（读到 CRLF 或超时/流结束为止）。 */
    private static String readReply(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                in.read(); // consume '\n'
                break;
            }
            sb.append((char) b);
            if (sb.length() > 64) {
                break;
            }
        }
        return sb.toString();
    }
}
