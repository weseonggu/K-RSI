package com.service.RSIranking.consumer;

import com.service.RSIranking.dto.RSIMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RSIStreanListener implements StreamListener<String, ObjectRecord<String, RSIMessageDTO>> {

    @Override
    public void onMessage(ObjectRecord<String, RSIMessageDTO> message) {
      log.info("시장 구분: " + message.getValue().getMkt_nm() + "종목 코드: " + message.getValue().getIsu_cd());
      // todo 여기에 메세지를 처리할 서비스와 로직을 구현하거나 서비스를 호출하는 코드 작성
    }

}
