package com.ai.service.pet;

import com.ai.model.dto.chat.ChatRequest;
import com.ai.model.dto.diary.DiaryEntrySaveRequest;
import com.ai.model.vo.chat.ChatConfigVO;
import com.ai.model.vo.chat.ChatConversationVO;
import com.ai.model.vo.chat.ChatMessageVO;
import com.ai.model.vo.chat.ChatStreamEvent;
import com.ai.model.vo.diary.DiaryEntryMonthItemVO;
import com.ai.model.vo.diary.DiaryEntryVO;
import com.ai.model.vo.tts.TtsVoiceVO;
import com.ai.service.pet.spi.PetChatBridge;
import com.ai.service.pet.spi.PetDiaryBridge;
import com.ai.service.pet.spi.PetTtsBridge;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Pet 能力门面（platform，始终注册）。
 * <p>
 * 只依赖 Bridge SPI（chat / diary / tts 各一个 Bean：真实实现与 NoOp XOR）。
 * 不注入 chat/diary/tts 业务 Service，避免关模块后启动 NPE 或循环依赖。
 * <p>
 * <b>鉴权约定：</b>本类业务方法不读取 Session、不检查登录态。
 * Pet 能力接口接受 {@code UserService.getLoginUser} 解析出的任意身份
 * （当前为 Session；后续 Device Bearer 过滤器可写入同一登录上下文，无需改 Facade）。
 * 设备绑定管理（仅 Session）不在本门面范围内。
 */
@Component
public class PetFacade {

    @Resource
    private PetChatBridge petChatBridge;

    @Resource
    private PetDiaryBridge petDiaryBridge;

    @Resource
    private PetTtsBridge petTtsBridge;

    public List<ChatConfigVO> chatConfigs() {
        return petChatBridge.listConfigs();
    }

    public ChatConversationVO chatResolve(Long userId, String configId) {
        return petChatBridge.resolveDefault(userId, configId);
    }

    public Page<ChatMessageVO> chatMessages(Long conversationId, Long userId, int pageNum, int pageSize) {
        return petChatBridge.listMessages(conversationId, userId, pageNum, pageSize);
    }

    public Flux<ChatStreamEvent> chatStream(ChatRequest request, HttpServletRequest httpRequest) {
        return petChatBridge.chat(request, httpRequest);
    }

    public DiaryEntryVO diaryToday(Long userId) {
        return petDiaryBridge.getByDate(LocalDate.now(), userId);
    }

    public DiaryEntryVO diaryByDate(LocalDate date, Long userId) {
        return petDiaryBridge.getByDate(date, userId);
    }

    public long diarySave(DiaryEntrySaveRequest request, Long userId) {
        return petDiaryBridge.save(request, userId);
    }

    public List<DiaryEntryMonthItemVO> diaryMonth(int year, int month, Long userId) {
        return petDiaryBridge.listByMonth(year, month, userId);
    }

    public List<TtsVoiceVO> ttsVoices() {
        return petTtsBridge.listVoices();
    }

    public byte[] ttsSynthesize(String text, Long voiceId, Map<String, Object> extra) {
        return petTtsBridge.synthesize(text, voiceId, extra);
    }

    public void requireEnabled() {
        petTtsBridge.requireEnabled();
    }
}
