package com.moewadeul.common.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** 응답 코드를 메시지 앞자리로 읽으므로, 메일 구현체를 올려 메시지 형식이 바뀌면 이 시험이 실제 클라이언트로 알린다. */
class SmtpReplyClassificationTest {

    @ParameterizedTest
    @CsvSource({
        "451 4.3.0 Temporary server error, true",
        "550 5.4.5 Daily user sending quota exceeded., true",
        "550 5.1.1 user unknown, false",
    })
    @DisplayName("[F-20] 실제 메일 클라이언트가 받은 SMTP 거부 응답을 일시적인 실패인지 가려낸다")
    void classifiesReplyThroughRealClient(String reply, boolean transientFailure) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread smtp = Thread.ofVirtual().start(() -> rejectMailFrom(server, reply));
            JavaMailSenderImpl client = new JavaMailSenderImpl();
            client.setHost(server.getInetAddress().getHostAddress());
            client.setPort(server.getLocalPort());
            SmtpMailSender sender = new SmtpMailSender(
                    client, new MailProperties("moewadeul@example.com", "뫼와들", Duration.ofSeconds(5)));

            assertThatThrownBy(() -> sender.send(new MailMessage("member@example.com", "제목", "본문")))
                    .isInstanceOfSatisfying(
                            MailDeliveryException.class,
                            e -> assertThat(e.isTransientFailure()).isEqualTo(transientFailure));

            sender.destroy();
            smtp.join(Duration.ofSeconds(5));
        }
    }

    private static void rejectMailFrom(ServerSocket server, String reply) {
        try (Socket socket = server.accept();
                BufferedReader in =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.US_ASCII)) {
            out.print("220 sink\r\n");
            out.flush();
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                String command = line.toUpperCase();
                if (command.startsWith("QUIT")) {
                    out.print("221 bye\r\n");
                    out.flush();
                    return;
                }
                out.print((command.startsWith("MAIL") ? reply : "250 ok") + "\r\n");
                out.flush();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
