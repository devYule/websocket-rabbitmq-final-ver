package com.green.rabbitmqchat.chat;

import com.green.rabbitmqchat.chat.model.ChatDto;
import com.green.rabbitmqchat.common.AppProperties;
import com.green.rabbitmqchat.common.RabbitMQProperties;
import com.green.rabbitmqchat.security.JwtTokenProvider;
import com.green.rabbitmqchat.security.MyPrincipal;
import com.green.rabbitmqchat.security.MyUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatMessageController {
    private final RabbitTemplate template;
    private final RabbitMQProperties properties;
    private final ChatService service;
    private final JwtTokenProvider jwtTokenProvider;
    private final AppProperties appProperties;

    @MessageMapping("chat.message.{ichatRoom}")
    public void send(ChatDto dto, @DestinationVariable long ichatRoom, StompHeaderAccessor accessor) {
        String authorizationHeader = accessor.getNativeHeader(appProperties.getJwt().getHeaderSchemeName()) == null ? null : String.valueOf(accessor.getNativeHeader(appProperties.getJwt().getHeaderSchemeName()).get(0));
        String token = authorizationHeader.substring(appProperties.getJwt().getTokenType().length() + 1);
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) jwtTokenProvider.getAuthentication(token);

        if (auth != null) {
            MyUserDetails myUserDetails = (MyUserDetails) auth.getPrincipal();
            MyPrincipal myPrincipal = myUserDetails.getMyPrincipal();
            dto.setSendIuser(myPrincipal.getIuser());
            dto.setSendUserNm(myPrincipal.getNm());
        }

        dto.setIchatRoom(ichatRoom);
        dto.setCreatedAt(LocalDateTime.now());
        service.send(dto);
        template.convertAndSend(properties.getChatExchangeName(), String.format("%s.%d", "room", ichatRoom), dto);
    }

    // queue name 도 exchange 이름과 동일하게 자동 생성된다.
    //    @RabbitListener(queues = "${spring.rabbitmq.chat-queue-name}")
    // 따라서 아래와 같이 해주는게 좋다.
    // 또한, 서버에서 Listener 역할을 하는 부분이기 때문에 key 를 room.* 로 설정해도 관계 없다.
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(name = "${spring.rabbitmq.chat-exchange-name}", type = ExchangeTypes.TOPIC),
            value = @Queue(name = "${spring.rabbitmq.chat-queue-name}"),
            key = "room.*"
    ))
    public void receive(ChatDto chat) {
        System.out.println("received : " + chat.getMessage());
    }

}
