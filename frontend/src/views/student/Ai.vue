<template>
  <div class="cg-page ai-page">
    <PageHeader title="AI 助手" description="可直接对话查询权益、下单、取消订单、查看任务进度">
      <el-button :icon="Plus" @click="newSession">新会话</el-button>
      <el-button :icon="Refresh" :loading="sessionLoading" @click="loadSessions">刷新会话</el-button>
    </PageHeader>

    <div class="cg-card ai-card">
      <div class="ai-layout">
        <!-- 会话列表 -->
        <aside class="ai-sessions">
          <div class="ai-sessions__head cg-flex-between">
            <span class="cg-text-2">会话</span>
            <el-button text size="small" :icon="Plus" @click="newSession" />
          </div>

          <el-skeleton v-if="sessionLoading" :rows="4" animated style="padding: 12px" />

          <EmptyState
            v-else-if="!sessions.length"
            compact
            title="暂无历史会话"
            description="直接输入问题即可开始"
            icon="search"
          />

          <div v-else>
            <div
              v-for="session in sessions"
              :key="session.sessionId"
              class="ai-session-item"
              :class="{ 'is-active': session.sessionId === activeSessionId }"
              @click="switchSession(session.sessionId)"
            >
              <div class="cg-flex-between cg-gap-8">
                <span class="cg-ellipsis ai-session-item__title">{{ session.title || '未命名会话' }}</span>
                <el-button
                  text
                  size="small"
                  :icon="Delete"
                  @click.stop="onDeleteSession(session)"
                />
              </div>
              <div class="cg-text-3 ai-session-item__meta">
                {{ formatDateTime(session.updateTime || session.createTime) }}
              </div>
            </div>
          </div>
        </aside>

        <!-- 对话区 -->
        <section class="ai-chat">
          <div ref="messagesRef" class="ai-messages">
            <EmptyState
              v-if="!messages.length && !sending"
              title="和 AI 助手聊聊"
              description="试试：帮我看看有哪些权益可以兑换 / 我今天的任务进度怎么样 / 帮我取消订单 xxx"
              icon="search"
            />

            <div
              v-for="(msg, index) in messages"
              :key="index"
              class="ai-msg"
              :class="msg.role === 'user' ? 'ai-msg--user' : 'ai-msg--assistant'"
            >
              <div class="ai-bubble">
                <div>{{ msg.content }}</div>

                <!-- 工具调用结果卡片 -->
                <div v-if="msg.toolCalls && msg.toolCalls.length" class="cg-mt-8">
                  <div v-for="(tool, ti) in msg.toolCalls" :key="ti" class="ai-tool-card">
                    <div class="ai-tool-card__head">
                      <span class="cg-flex-center cg-gap-8">
                        <el-icon><Tools /></el-icon>
                        <b>{{ tool.name || 'tool' }}</b>
                        <el-tag size="small" effect="plain" :type="tool.success === false ? 'danger' : 'success'">
                          {{ tool.success === false ? '失败' : '成功' }}
                        </el-tag>
                      </span>
                      <span>{{ num(tool.costMs) }} ms</span>
                    </div>
                    <div v-if="tool.args" class="cg-text-3 cg-mt-8">入参</div>
                    <pre v-if="tool.args" class="ai-tool-card__pre">{{ pretty(tool.args) }}</pre>
                    <div v-if="tool.result" class="cg-text-3 cg-mt-8">返回</div>
                    <pre v-if="tool.result" class="ai-tool-card__pre">{{ pretty(tool.result) }}</pre>
                  </div>
                </div>

                <div v-if="msg.model || msg.costMs" class="ai-bubble__meta">
                  <span v-if="msg.model">{{ msg.model }}</span>
                  <span v-if="msg.costMs">{{ num(msg.costMs) }} ms</span>
                </div>
              </div>
            </div>

            <div v-if="sending" class="ai-msg ai-msg--assistant">
              <div class="ai-bubble">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span class="cg-text-3">助手正在思考…</span>
              </div>
            </div>
          </div>

          <div class="ai-input">
            <el-input
              v-model="input"
              type="textarea"
              :rows="2"
              resize="none"
              maxlength="500"
              show-word-limit
              placeholder="输入你的问题，Enter 发送，Shift + Enter 换行"
              @keydown.enter.exact.prevent="onSend"
            />
            <div class="ai-input__actions">
              <span class="cg-text-3 ai-input__hint">
                会话：{{ activeSessionId || '尚未创建' }}
              </span>
              <el-button type="primary" :icon="Promotion" :loading="sending" :disabled="!input.trim()" @click="onSend">
                发送
              </el-button>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Loading, Plus, Promotion, Refresh, Tools } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import { chat, deleteSession, getMessages, getSessions } from '@/api/ai'
import { formatDateTime, genSessionId, num } from '@/utils/format'
import { handleError } from '@/utils/errorHandler'

/**
 * AI 助手。接口：
 *   GET    /api/ai/sessions
 *   GET    /api/ai/messages?sessionId=
 *   POST   /api/ai/chat   {sessionId,message}
 *   DELETE /api/ai/session/{sessionId}
 * 工具调用（toolCalls）来自后端 Function Calling 审计，前端只做展示。
 */
const sessionLoading = ref(false)
const sending = ref(false)
const sessions = ref([])
const messages = ref([])
const activeSessionId = ref('')
const input = ref('')
const messagesRef = ref(null)

const pretty = (value) => {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'string') {
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch (e) {
      return value
    }
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch (e) {
    return String(value)
  }
}

const scrollToBottom = async () => {
  await nextTick()
  const el = messagesRef.value
  if (el) el.scrollTop = el.scrollHeight
}

const loadSessions = async () => {
  sessionLoading.value = true
  try {
    const data = await getSessions()
    sessions.value = Array.isArray(data) ? data : []
  } catch (e) {
    sessions.value = []
  } finally {
    sessionLoading.value = false
  }
}

/** 历史消息按后端 role 渲染，tool 角色合并进上一条助手消息的 toolCalls */
const loadMessages = async (sessionId) => {
  if (!sessionId) {
    messages.value = []
    return
  }
  try {
    const data = await getMessages(sessionId)
    const list = Array.isArray(data) ? data : []
    const normalized = []
    list.forEach((item) => {
      if (item.role === 'tool') {
        const last = normalized[normalized.length - 1]
        const toolCall = {
          name: item.toolName,
          args: item.toolArgs,
          result: item.toolResult,
          costMs: item.costMs
        }
        if (last && last.role === 'assistant') {
          last.toolCalls = [...(last.toolCalls || []), toolCall]
        } else {
          normalized.push({ role: 'assistant', content: '', toolCalls: [toolCall] })
        }
        return
      }
      normalized.push({
        role: item.role || 'assistant',
        content: item.content || '',
        model: item.model,
        costMs: item.costMs
      })
    })
    messages.value = normalized
    await scrollToBottom()
  } catch (e) {
    messages.value = []
  }
}

const switchSession = async (sessionId) => {
  activeSessionId.value = sessionId
  await loadMessages(sessionId)
}

const newSession = () => {
  activeSessionId.value = genSessionId()
  messages.value = []
  input.value = ''
  ElMessage.success('已创建新会话，直接提问即可')
}

const onDeleteSession = async (session) => {
  try {
    await ElMessageBox.confirm('确认删除该会话及其消息记录？', '删除会话', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await deleteSession(session.sessionId)
    ElMessage.success('会话已删除')
    if (activeSessionId.value === session.sessionId) {
      activeSessionId.value = ''
      messages.value = []
    }
    await loadSessions()
  } catch (e) {
    handleError(e, '删除会话')
  }
}

const onSend = async () => {
  const text = input.value.trim()
  if (!text || sending.value) return

  // 首次对话若没有会话 ID，由前端生成一个，后端会按 sessionId 落库
  if (!activeSessionId.value) {
    activeSessionId.value = genSessionId()
  }

  messages.value.push({ role: 'user', content: text })
  input.value = ''
  sending.value = true
  await scrollToBottom()

  try {
    const data = await chat({ sessionId: activeSessionId.value, message: text })
    messages.value.push({
      role: 'assistant',
      content: data?.reply || '(助手没有返回内容)',
      toolCalls: Array.isArray(data?.toolCalls) ? data.toolCalls : [],
      model: data?.model,
      costMs: data?.costMs
    })
    if (data?.sessionId) activeSessionId.value = data.sessionId
    await loadSessions()
  } catch (e) {
    // 大模型不可用等异常由拦截器提示，这里补一条本地提示，保持对话可继续
    messages.value.push({
      role: 'assistant',
      content: '抱歉，这次请求没有成功。可以稍后重试，或直接到权益商城手动操作。'
    })
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

onMounted(loadSessions)
</script>

<style scoped>
.ai-card {
  overflow: hidden;
}

.ai-page :deep(.cg-card__body) {
  padding: 0;
}

.ai-sessions__head {
  padding: 10px 12px;
  border-bottom: 1px solid var(--cg-border-light);
  font-size: 13px;
}

.ai-session-item__title {
  font-size: 13px;
  font-weight: 600;
}

.ai-session-item__meta {
  margin-top: 4px;
  font-size: 12px;
}

.ai-bubble__meta {
  margin-top: 6px;
  display: flex;
  gap: 10px;
  font-size: 11px;
  opacity: 0.75;
}

.ai-input__actions {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.ai-input__hint {
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-chat {
  min-height: 520px;
}

@media (max-width: 768px) {
  .ai-chat {
    min-height: 440px;
  }

  .ai-input__hint {
    display: none;
  }

  .ai-input__actions {
    justify-content: flex-end;
  }
}
</style>
