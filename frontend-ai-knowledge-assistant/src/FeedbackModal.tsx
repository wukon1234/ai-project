import { useState } from 'react'
import { X } from 'lucide-react'

const feedbackTypes = [
  '答案不准确',
  '引用了错误的文档',
  '没有找到应该有的知识',
  '回答不完整',
  '其他'
] as const

type FeedbackModalProps = {
  open: boolean
  onClose: () => void
  onSubmitted: (message: string) => void
}

function FeedbackModal({ open, onClose, onSubmitted }: FeedbackModalProps) {
  const [type, setType] = useState<(typeof feedbackTypes)[number]>('答案不准确')
  const [note, setNote] = useState('')
  const [knowAnswer, setKnowAnswer] = useState(false)
  const [correctAnswer, setCorrectAnswer] = useState('')

  if (!open) return null

  function onSubmit() {
    onSubmitted('感谢反馈，我们会尽快优化 🙏')
    setNote('')
    setCorrectAnswer('')
    setKnowAnswer(false)
    setType('答案不准确')
    onClose()
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
                <label key={item} className={`fbRadio ${type === item ? 'fbRadioActive' : ''}`}>
                  <input
                    type="radio"
                    name="feedback-type"
                    checked={type === item}
                    onChange={() => setType(item)}
                  />
                  <span>{item}</span>
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
        </div>

        <div className="fbFooter">
          <button type="button" className="fbCancel" onClick={onClose}>
            取消
          </button>
          <button type="button" className="fbSubmit" onClick={onSubmit}>
            提交反馈
          </button>
        </div>
      </div>
    </div>
  )
}

export default FeedbackModal
