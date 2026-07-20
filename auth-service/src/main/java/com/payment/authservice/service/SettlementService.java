package com.payment.authservice.service;

import tools.jackson.databind.ObjectMapper;
import com.payment.authservice.client.KakaoApiClient;
import com.payment.authservice.dto.FriendResponse;
import com.payment.authservice.dto.KakaoFriendsResponse;
import com.payment.authservice.dto.KakaoMessageResponse;
import com.payment.authservice.dto.PaymentDetail;
import com.payment.authservice.dto.SettlementMessageResponse;
import com.payment.authservice.dto.SettlementMessageResponse.MessageResult;
import com.payment.authservice.dto.SettlementMessageResponse.PersonSettlement;
import com.payment.authservice.dto.SettlementRequest;
import com.payment.authservice.dto.SettlementResult;
import com.payment.authservice.dto.SettlementResult.PaymentBreakdown;
import com.payment.authservice.repository.MemberRepository;
import com.payment.common.domain.Member;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final KakaoApiClient kakaoApiClient;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public SettlementMessageResponse calculateAndSendSettlement(Long memberId, SettlementRequest request) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND, "존재하지 않는 회원입니다."));

        if (member.getKakaoAccessToken() == null) {
            throw new CustomException(CustomErrorCode.UNAUTHORIZED, "카카오 토큰이 만료되었습니다. 다시 로그인해주세요.");
        }

        // Calculate settlement
        SettlementResult result = calculateSettlement(request.payments());

        // Get friend nicknames for response
        Map<String, String> uuidToNickname = getFriendNicknames(member.getKakaoAccessToken(), request.receiverUuids());

        // Send messages to each receiver
        MessageResult messageResult = sendSettlementMessages(
            member.getKakaoAccessToken(),
            member.getNickname() != null ? member.getNickname() : member.getName(),
            request.receiverUuids(),
            result,
            uuidToNickname
        );

        // Build response
        List<PersonSettlement> settlements = result.personSettlements().stream()
            .filter(ps -> request.receiverUuids().contains(ps.uuid()))
            .map(ps -> new PersonSettlement(
                ps.uuid(),
                uuidToNickname.getOrDefault(ps.uuid(), "Unknown"),
                ps.totalAmount()
            ))
            .toList();

        return new SettlementMessageResponse(
            result.totalAmount(),
            settlements,
            messageResult
        );
    }

    private SettlementResult calculateSettlement(List<PaymentDetail> payments) {
        int totalAmount = payments.stream().mapToInt(PaymentDetail::amount).sum();

        // Calculate payment breakdowns
        List<PaymentBreakdown> breakdowns = new ArrayList<>();
        Map<String, List<SettlementResult.PersonSettlement.PaymentShare>> personShares = new HashMap<>();

        for (PaymentDetail payment : payments) {
            int participantCount = payment.participantUuids().size();
            int amountPerPerson = payment.amount() / participantCount;

            breakdowns.add(new PaymentBreakdown(
                payment.description(),
                payment.amount(),
                participantCount,
                amountPerPerson,
                payment.participantUuids()
            ));

            // Add share for each participant
            for (String uuid : payment.participantUuids()) {
                personShares.computeIfAbsent(uuid, k -> new ArrayList<>())
                    .add(new SettlementResult.PersonSettlement.PaymentShare(
                        payment.description(),
                        amountPerPerson
                    ));
            }
        }

        // Build person settlements
        List<SettlementResult.PersonSettlement> personSettlements = personShares.entrySet().stream()
            .map(entry -> {
                int total = entry.getValue().stream()
                    .mapToInt(SettlementResult.PersonSettlement.PaymentShare::amount)
                    .sum();
                return new SettlementResult.PersonSettlement(entry.getKey(), total, entry.getValue());
            })
            .toList();

        return new SettlementResult(totalAmount, breakdowns, personSettlements);
    }

    private Map<String, String> getFriendNicknames(String kakaoAccessToken, List<String> uuids) {
        Map<String, String> nicknames = new HashMap<>();
        try {
            KakaoFriendsResponse friends = kakaoApiClient.getFriends(
                "Bearer " + kakaoAccessToken, 0, 100
            );
            if (friends.elements() != null) {
                for (KakaoFriendsResponse.KakaoFriend friend : friends.elements()) {
                    if (uuids.contains(friend.uuid())) {
                        nicknames.put(friend.uuid(), friend.profileNickname());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get friend nicknames: {}", e.getMessage());
        }
        return nicknames;
    }

    private MessageResult sendSettlementMessages(
        String kakaoAccessToken,
        String senderName,
        List<String> receiverUuids,
        SettlementResult result,
        Map<String, String> uuidToNickname
    ) {
        List<String> successUuids = new ArrayList<>();
        List<String> failedUuids = new ArrayList<>();

        for (String uuid : receiverUuids) {
            try {
                String message = buildSettlementMessage(senderName, uuid, result, uuidToNickname);
                String templateObject = buildTemplateObject(message);

                KakaoMessageResponse response = kakaoApiClient.sendMessage(
                    "Bearer " + kakaoAccessToken,
                    "[\"" + uuid + "\"]",
                    templateObject
                );

                if (response.successfulReceiverUuids() != null && !response.successfulReceiverUuids().isEmpty()) {
                    successUuids.add(uuid);
                } else {
                    failedUuids.add(uuid);
                }
            } catch (Exception e) {
                log.error("Failed to send message to {}: {}", uuid, e.getMessage());
                failedUuids.add(uuid);
            }
        }

        return new MessageResult(successUuids.size(), failedUuids);
    }

    private String buildSettlementMessage(
        String senderName,
        String receiverUuid,
        SettlementResult result,
        Map<String, String> uuidToNickname
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("💰 정산 요청\n\n");
        sb.append("📋 결제 내역\n");

        for (PaymentBreakdown breakdown : result.paymentBreakdowns()) {
            sb.append(String.format("• %s: %,d원 (%d명, 1인당 %,d원)\n",
                breakdown.description(),
                breakdown.amount(),
                breakdown.participantCount(),
                breakdown.amountPerPerson()
            ));
        }

        sb.append(String.format("\n💵 총 결제 금액: %,d원\n\n", result.totalAmount()));

        // Find this person's settlement
        result.personSettlements().stream()
            .filter(ps -> ps.uuid().equals(receiverUuid))
            .findFirst()
            .ifPresent(ps -> {
                sb.append("📌 회원님의 정산 내역\n");
                for (var share : ps.shares()) {
                    sb.append(String.format("• %s: %,d원\n", share.description(), share.amount()));
                }
                sb.append(String.format("\n✅ 총 정산 금액: %,d원\n", ps.totalAmount()));
            });

        sb.append(String.format("\n보낸 사람: %s", senderName));

        return sb.toString();
    }

    private String buildTemplateObject(String message) {
        Map<String, Object> template = new HashMap<>();
        template.put("object_type", "text");
        template.put("text", message);
        template.put("link", Map.of(
            "web_url", "https://nanugi.com",
            "mobile_web_url", "https://nanugi.com"
        ));
        return objectMapper.writeValueAsString(template);
    }
}
