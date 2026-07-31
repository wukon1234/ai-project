type Props = {
  title: string
  description: string
}

export default function AdminPlaceholder({ title, description }: Props) {
  return (
    <div className="adminPage">
      <div className="adminPlaceholder">
        <h1>{title}</h1>
        <p>{description}</p>
        <span className="adminMuted">模块建设中 · 后续批次实现</span>
      </div>
    </div>
  )
}
