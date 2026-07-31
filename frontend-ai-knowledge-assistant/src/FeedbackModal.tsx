import { useState } from 'react'
import { X } from 'lucide-react'
import { feedbackUnhelpful, type FeedbackIssueType } from './api'

const feedbackTypes = [
  { label: '答案不准确', value: 'INACCURATE' as const },
  { label: '引用了错误的文档', value: 'WRONG_DOC' as const },
  { label: '没有找到应该有的知识', value: 'MISSING_KNOWLEDGE' as const },
  { label: '回答不完整', value: 'INCOMPLETE' as const },
  { label: '其他', value: 'OTHER' as const },
]

type FeedbackModalProps = {
  open: boolean
  messageId: number | null
  onClose: () => void
  onSubmitted: (message: string) => void
}

function FeedbackModal({ open, messageId, onClose, onSubmitted }: FeedbackModalProps) {
  const [issueType, setIssueType] = useState<FeedbackIssueType>('INACCURATE')
  const [note, setNote] = useState('')
  const [knowAnswer, setKnowAnswer] = useState(false)
  const [correctAnswer, setCorrectAnswer] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!open) return null

  function resetForm() {
    setNote('')
    setCorrectAnswer('')
    setKnowAnswer(false)
    setIssueType('INACCURATE')
    setError(null)
  }

  async function onSubmit() {
    if (!messageId) {
      setError('缺少回答消息，请先完成一次问答')
      return
    }
    if (knowAnswer && !correctAnswer.trim()) {
      setError('勾选「我知道正确答案」时请填写正确答案')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      const message = await feedbackUnhelpful({
        messageId,
        issueType,
        comment: note.trim() || undefined,
        knowCorrect: knowAnswer,
        correctAnswer: knowAnswer ? correctAnswer.trim() : undefined,
      })
      onSubmitted(message)
      resetForm()
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : '提交失败')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fbOverlay" role="presentation" onClick={onClose}>
      <div
        className="fbModal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="fb-title"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="fbHeader">
          <div>
            <h2 id="fb-title">帮助我们改进</h2>
            <p>您的反馈将用于优化知识检索质量</p>
          </div>
          <button type="button" className="fbClose" onClick={onClose} aria-label="关闭">
            <X size={18} />
          </button>
        </div>

        <div className="fbBody">
          <div className="fbField">
            <div className="fbLabel">问题类型</div>
            <div className="fbRadioList">
              {feedbackTypes.map((item) => (
                <label key={item.value} className={`fbRadio ${issueType === item.value ? 'fbRadioActive' : ''}`}>
                  <input
                    type="radio"
                    name="feedback-type"
                    checked={issueType === item.value}
                    onChange={() => setIssueType(item.value)}
                  />
                  <span>{item.label}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="fbField">
            <div className="fbLabel">补充说明（可选）</div>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="请描述正确的答案或遗漏的信息…"
              rows={4}
            />
          </div>

          <div className="fbField">
            <label className="fbToggle">
              <input
                type="checkbox"
                checked={knowAnswer}
                onChange={(e) => setKnowAnswer(e.target.checked)}
              />
              <span>我知道正确答案</span>
            </label>
            {knowAnswer ? (
              <textarea
                className="fbCorrect"
                value={correctAnswer}
                onChange={(e) => setCorrectAnswer(e.target.value)}
                placeholder="请填写你认为正确的答案…"
                rows={3}
              />
            ) : null}
          </div>

          {error ? <div className="fbError" role="alert">{error}</div> : null}
        </div>

        <div className="fbFooter">
          <button type="button" className="fbCancel" onClick={onClose} disabled={submitting}>
            取消
          </button>
          <button type="button" className="fbSubmit" onClick={onSubmit} disabled={submitting}>
            {submitting ? '提交中…' : '提交反馈'}
          </button>
        </div>
      </div>
    </div>
  )
}

export default FeedbackModal
