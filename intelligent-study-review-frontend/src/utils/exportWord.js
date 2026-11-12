import {
  Document, Packer, Paragraph, TextRun, AlignmentType, BorderStyle, TabStopType
} from 'docx'
import { saveAs } from 'file-saver'

/**
 * 导出 Word 试卷。
 *
 * 版式对齐页面上的卷面（而不是一份「数据清单」）：
 * 卷头（标题 / 科目·年级·题数·满分·时长 / 留空的考生信息）→ 注意事项 → 按题型分大题
 * （中文序号 + 右侧得分栏）→ 题目与选项。含答案版在每题下方接参考答案与解析。
 */
const typeMap = {
  SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题',
  SHORT_ANSWER: '简答题', BLANK_FILLING: '填空题', PROOF_QUESTION: '证明题',
  MATERIAL: '材料题', ESSAY: '论述题'
}
const CN_NUM = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']
const cnNum = i => CN_NUM[i] || String(i + 1)
const letter = i => String.fromCharCode(65 + i)
// 选项自带序号（"A. xxx"）时去掉，序号由模板统一给
const optText = opt => String(opt || '').replace(/^[A-Z][.、]\s*/, '')

/** A4 纸的内容宽度（twips）：11906 - 2×1134 */
const CONTENT_WIDTH = 9638

function rule() {
  return new Paragraph({
    spacing: { after: 160 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: 'BFBFBF' } }
  })
}

function notesParagraphs(paper, sections) {
  const typeText = sections.map(t => `${t.label} ${t.items.length} 题，共 ${t.score} 分`).join('；')
  const lines = [
    `本试卷共 ${paper.questions?.length || 0} 题，满分 ${paper.totalScore} 分，考试时间 ${paper.durationMinutes} 分钟。`,
    `试卷分 ${sections.length} 部分：${typeText}。`,
    `选择题在选项前勾选作答${sections.some(t => t.type === 'MULTIPLE_CHOICE') ? '，多选题全部选对才得分' : ''}；其余题目在答题区内作答。`,
    '考试结束后，将试卷与答题卡一并交回。'
  ]
  return [
    new Paragraph({
      spacing: { before: 120, after: 60 },
      children: [new TextRun({ text: '注意事项', bold: true, size: 21 })]
    }),
    ...lines.map((text, i) => new Paragraph({
      // 悬挂缩进：序号在左，折行后与正文对齐
      indent: { left: 620, hanging: 460 },
      spacing: { after: 40 },
      children: [new TextRun({ text: `${i + 1}．${text}`, size: 20, color: '333333' })]
    }))
  ]
}

/**
 * @param {object} paper - 试卷数据（paperName, subject, gradeOrLevel, totalScore, durationMinutes, questions）
 * @param {boolean} withAnswers - 是否包含答案
 */
export async function exportWord(paper, withAnswers) {
  const children = []

  // 先按 sortOrder 排序，再按题型分大题 —— 与页面 / PDF 导出同一口径
  const questions = [...(paper.questions || [])]
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))

  const sectionMap = new Map()
  questions.forEach((q, i) => {
    const t = q.questionType
    if (!sectionMap.has(t)) sectionMap.set(t, [])
    // 真实题号随题目一起带走：分组后组内下标不再是全卷题号
    sectionMap.get(t).push({ q, no: q.sortOrder > 0 ? q.sortOrder : i + 1 })
  })
  const sections = [...sectionMap.entries()].map(([type, items]) => ({
    type,
    label: typeMap[type] || type,
    items,
    score: items.reduce((s, it) => s + (Number(it.q.score) || 0), 0)
  }))

  // ===== 卷头 =====
  children.push(
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 100 },
      children: [new TextRun({ text: paper.paperName || '试卷', bold: true, size: 36 })]
    })
  )

  const meta = [paper.subject, paper.gradeOrLevel,
    `共 ${questions.length} 题`, `满分 ${paper.totalScore} 分`, `限时 ${paper.durationMinutes} 分钟`
  ].filter(Boolean).join('    ')
  children.push(
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 160 },
      children: [new TextRun({ text: meta, size: 20, color: '444444' })]
    })
  )

  // 考生信息留空，与卷面一致
  children.push(
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 160 },
      children: [new TextRun({ text: '姓名：＿＿＿＿＿　　班级：＿＿＿＿＿　　学号：＿＿＿＿＿　　得分：＿＿＿＿＿', size: 20 })]
    })
  )

  children.push(rule())
  children.push(...notesParagraphs(paper, sections))

  // ===== 题目 =====
  sections.forEach((sec, si) => {
    children.push(
      new Paragraph({
        spacing: { before: 300, after: 100 },
        tabStops: [{ type: TabStopType.RIGHT, position: CONTENT_WIDTH }],
        children: [
          new TextRun({
            text: `${cnNum(si)}、${sec.label}（共 ${sec.items.length} 题，共 ${sec.score} 分）`,
            bold: true, size: 22
          }),
          new TextRun({ text: '\t得分：＿＿＿＿', size: 18, color: '666666' })
        ]
      })
    )

    sec.items.forEach(({ q, no }) => {
      children.push(
        new Paragraph({
          indent: { left: 440, hanging: 440 },
          spacing: { before: 100, after: 60 },
          children: [new TextRun({ text: `${no}．${q.content}`, size: 21 })]
        })
      )

      if (q.materialText) {
        children.push(
          new Paragraph({
            indent: { left: 440 },
            spacing: { after: 60 },
            children: [new TextRun({ text: `【材料】${q.materialText}`, size: 19, color: '555555' })]
          })
        )
      }

      (q.options || []).forEach((opt, i) => {
        children.push(
          new Paragraph({
            indent: { left: 880 },
            spacing: { after: 20 },
            children: [new TextRun({ text: `${letter(i)}．${optText(opt)}`, size: 20 })]
          })
        )
      })

      if (withAnswers && q.answer) {
        children.push(
          new Paragraph({
            indent: { left: 440 },
            spacing: { before: 60 },
            children: [new TextRun({ text: `参考答案：${q.answer}`, size: 20, bold: true, color: '1F7A45' })]
          })
        )
        if (q.analysis) {
          children.push(
            new Paragraph({
              indent: { left: 440 },
              spacing: { after: 40 },
              children: [new TextRun({ text: `解析：${q.analysis}`, size: 19, color: '555555' })]
            })
          )
        }
        if (q.knowledgePoints?.length) {
          children.push(
            new Paragraph({
              indent: { left: 440 },
              spacing: { after: 40 },
              children: [new TextRun({ text: `知识点：${q.knowledgePoints.join(' · ')}`, size: 18, color: '888888' })]
            })
          )
        }
      }
    })
  })

  const doc = new Document({
    styles: {
      default: {
        document: { run: { font: 'SimSun', size: 21 } }
      }
    },
    sections: [{
      properties: {
        page: {
          size: { width: 11906, height: 16838 },   // A4
          margin: { top: 1134, right: 1134, bottom: 1134, left: 1134 }
        }
      },
      children
    }]
  })

  const blob = await Packer.toBlob(doc)
  // 文件名区分含答案版，与 PDF 导出的命名口径对齐（否则同卷导出两份会变成「试卷名(1).docx」，分不清）
  saveAs(blob, `${paper.paperName || '试卷'}${withAnswers ? '（含答案）' : ''}.docx`)
}
