package com.service.RSIranking.batch.rsi_calculation_job.step;

import com.service.RSIranking.dto.RSIMessageDTO;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

public class MessageProduceWriter implements ItemWriter<RSIMessageDTO> {
    @Override
    public void write(Chunk<? extends RSIMessageDTO> chunk) throws Exception {

    }
}
