package com.service.RSIranking.consumer;

import com.service.RSIranking.dto.RSIMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

/**
 * RSI 메시지 Stream Listener.
 *
 * <p>Redis Stream에서 수신된 RSI 메시지를 처리하는 리스너입니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Component
@Slf4j
public class RSIStreanListener implements StreamListener<String, ObjectRecord<String, RSIMessageDTO>> {

    /**
     * Redis Stream에서 메시지를 수신했을 때 호출됩니다.
     *
     * @param message 수신된 RSI 메시지
     */
    @Override
    public void onMessage(ObjectRecord<String, RSIMessageDTO> message) {
      log.info("시장 구분: " + message.getValue().getMkt_nm() + "종목 코드: " + message.getValue().getIsu_cd());
      // todo 여기에 메세지를 처리할 서비스와 로직을 구현하거나 서비스를 호출하는 코드 작성
    }

}
