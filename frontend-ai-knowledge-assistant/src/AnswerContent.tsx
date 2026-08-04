import type { ReactNode } from 'react'
import type { StreamCitation } from './api'

type AnswerContentProps = {
  text: string
  citations?: StreamCitation[]
  streaming?: boolean
  onOpenCitation?: (cite: StreamCitation) => void
}

/** 将模型 Markdown 转为结构化排版，并去掉原始 # * - 等符号展示。 */
export default function AnswerContent({
  text,
  citations = [],
  streaming = false,
  onOpenCitation,
}: AnswerContentProps) {
  const citeMap = new Map(citations.map((c) => [c.index, c]))
  const blocks = parseBlocks(text || '')

  return (
    <div className={`qaRichAnswer ${streaming ? 'qaRichAnswerStreaming' : ''}`}>
      {blocks.map((block, i) => renderBlock(block, i, citeMap, onOpenCitation))}
      {streaming ? <span className="qaStreamCaret" aria-hidden="true" /> : null}
    </div>
  )
}

type TextBlock = { type: 'h1' | 'h2' | 'h3'; text: string }
type ParagraphBlock = { type: 'p'; text: string }
type ListBlock = { type: 'ul' | 'ol'; items: string[] }
type Block = TextBlock | ParagraphBlock | ListBlock

function parseBlocks(raw: string): Block[] {
  const lines = raw.replace(/\r\n/g, '\n').split('\n')
  const blocks: Block[] = []
  let i = 0

  while (i < lines.length) {
    const line = lines[i]
    const trimmed = line.trim()
    if (!trimmed) {
      i += 1
      continue
    }

    const heading = trimmed.match(/^(#{1,3})\s+(.*)$/)
    if (heading) {
      const level = heading[1].length as 1 | 2 | 3
      const type = level === 1 ? 'h1' : level === 2 ? 'h2' : 'h3'
      blocks.push({ type, text: stripInlineMd(heading[2]) })
      i += 1
      continue
    }

    if (/^[-*•]\s+/.test(trimmed)) {
      const items: string[] = []
      while (i < lines.length) {
        const t = lines[i].trim()
        if (!t) break
        const m = t.match(/^[-*•]\s+(.*)$/)
        if (!m) break
        items.push(stripInlineMd(m[1]))
        i += 1
      }
      blocks.push({ type: 'ul', items })
      continue
    }

    if (/^\d+[.)、]\s+/.test(trimmed)) {
      const items: string[] = []
      while (i < lines.length) {
        const t = lines[i].trim()
        if (!t) break
        const m = t.match(/^\d+[.)、]\s+(.*)$/)
        if (!m) break
        items.push(stripInlineMd(m[1]))
        i += 1
      }
      blocks.push({ type: 'ol', items })
      continue
    }

    const parts: string[] = [stripInlineMd(trimmed)]
    i += 1
    while (i < lines.length) {
      const t = lines[i].trim()
      if (!t) break
      if (/^(#{1,3})\s+/.test(t) || /^[-*•]\s+/.test(t) || /^\d+[.)、]\s+/.test(t)) break
      parts.push(stripInlineMd(t))
      i += 1
    }
    blocks.push({ type: 'p', text: parts.join(' ') })
  }

  return blocks
}

function stripInlineMd(text: string): string {
  return text
    .replace(/^#{1,6}\s+/, '')
    .replace(/^[-*•]\s+/, '')
    .replace(/^\d+[.)、]\s+/, '')
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/__(.+?)__/g, '$1')
    .replace(/\*(.+?)\*/g, '$1')
    .replace(/_(.+?)_/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^>\s?/, '')
    .trim()
}

function renderBlock(
  block: Block,
  key: number,
  citeMap: Map<number, StreamCitation>,
  onOpenCitation?: (cite: StreamCitation) => void,
): ReactNode {
  switch (block.type) {
    case 'ul':
    case 'ol': {
      const Tag = block.type === 'ul' ? 'ul' : 'ol'
      return (
        <Tag key={key} className={block.type === 'ul' ? 'qaRichList' : 'qaRichList qaRichListOrdered'}>
          {block.items.map((item, idx) => (
            <li key={idx}>{renderInline(item, citeMap, onOpenCitation)}</li>
          ))}
        </Tag>
      )
    }
    case 'p':
      return (
        <p key={key} className="qaRichParagraph">
          {renderInline(block.text, citeMap, onOpenCitation)}
        </p>
      )
    case 'h1':
    case 'h2':
    case 'h3':
      return (
        <div key={key} className={`qaRichHeading qaRichHeading-${block.type}`}>
          {renderInline(block.text, citeMap, onOpenCitation)}
        </div>
      )
  }
}

function renderInline(
  text: string,
  citeMap: Map<number, StreamCitation>,
  onOpenCitation?: (cite: StreamCitation) => void,
): ReactNode[] {
  const nodes: ReactNode[] = []
  const re = /\[(\d+)\]/g
  let last = 0
  let match: RegExpExecArray | null
  let key = 0

  while ((match = re.exec(text)) !== null) {
    if (match.index > last) {
      nodes.push(<span key={`t-${key++}`}>{text.slice(last, match.index)}</span>)
    }
    const index = Number(match[1])
    const cite = citeMap.get(index)
    if (cite && onOpenCitation) {
      nodes.push(
        <button
          key={`c-${key++}`}
          type="button"
          className="qaCiteMark"
          title={`${cite.title} · 第 ${cite.page} 页`}
          onClick={() => onOpenCitation(cite)}
        >
          [{index}]
        </button>,
      )
    } else {
      nodes.push(
        <sup key={`c-${key++}`} className="qaCiteMark qaCiteMarkMuted">
          [{index}]
        </sup>,
      )
    }
    last = match.index + match[0].length
  }

  if (last < text.length) {
    nodes.push(<span key={`t-${key++}`}>{text.slice(last)}</span>)
  }
  return nodes
}
