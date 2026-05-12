package com.yef.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yef.UpgradeEventMessage;
import com.yef.req.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import java.util.UUID;

/**
 * @description: 基于升级状态流转 状态变化的事件消息推送。
 * gateway 只负责生产事件，web-http-server 消费 MQ 后负责更新 DB / 推送 WebSocket。
 * @author: 叶丰
 * @date: 2026/4/28 20:34
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceUpgradeEventPushClient {

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Value("${rocketmq.topic.upgrade-event:FOTA_UPGRADE_EVENT_TOPIC}")
    private String upgradeEventTopic;

    /**
     * 推送升级开始时间。升级开始时间的定义：由网关下发设备的第一个分包数据开始为准
     * @param request request
     */
    public void updateStartTime(UpgradeStartTimeEventRequest request) {
        sendUpgradeEvent(UpgradeEventMessage.EventType.START_TIME, "START_TIME", request);
    }


    /**
     * 推送UPGRADING状态。只推送一次
     * @param request request
     */
    public void pushEntryIntoUpgradingStatus(EntryUpgradingEventRequest request) {
        sendUpgradeEvent(UpgradeEventMessage.EventType.UPGRADING, "UPGRADING", request);
    }



    /**
     * 推送升级进度progress【进度条】
     * @param eventRequest eventRequest
     */
    public void pushUpgradeProgress(UpgradeProgressEventRequest eventRequest) {
        sendUpgradeEvent(UpgradeEventMessage.EventType.PROGRESS, "PROGRESS", eventRequest);
    }


    /**
     * 推送设备掉线状态
     * @param request request
     */
    public void pushDeviceDisconnectStatus(DisconnectEventRequest request) {
        sendUpgradeEvent(UpgradeEventMessage.EventType.DISCONNECT, "DISCONNECT", request);
    }


    /**
     * 推送最终升级结果。
     * @param request request
     */
    public void updateFinalUpgradeTaskRecord(UpgradeFinalResultEventRequest request) {
        sendUpgradeEvent(UpgradeEventMessage.EventType.FINAL_RESULT, "FINAL_RESULT", request);
    }

    /**
     * 推送取消升级结果
     * @param request request
     */
    public void updateCancelResult(UpgradeCancelEventRequest request) {
        sendUpgradeEvent(UpgradeEventMessage.EventType.CANCEL_RESULT, "CANCEL_RESULT", request);
    }


    private void sendUpgradeEvent(String eventType, String tag, Object source) {

        UpgradeEventMessage eventMessage = objectMapper.convertValue(source, UpgradeEventMessage.class);
        eventMessage.setEventId(UUID.randomUUID().toString().replace("-", ""));
        eventMessage.setEventType(eventType);
        eventMessage.setEventTime(System.currentTimeMillis());

        String destination = upgradeEventTopic + ":" + tag;
        //rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(eventMessage).build());
        rocketMQTemplate.asyncSend(
                destination,
                MessageBuilder.withPayload(eventMessage).build(),
                new SendCallback() {
                    @Override
                    public void onSuccess(SendResult sendResult) {
                        log.debug("MQ发送成功, eventId={}, msgId={}", eventMessage.getEventId(), sendResult.getMsgId());
                    }

                    @Override
                    public void onException(Throwable e) {
                        log.error("MQ发送失败, eventId={}", eventMessage.getEventId(), e);
                    }
                }
        );

        log.debug("升级事件已发送到 MQ, eventId={}, eventType={}, destination={}", eventMessage.getEventId(), eventType, destination);
    }



}
