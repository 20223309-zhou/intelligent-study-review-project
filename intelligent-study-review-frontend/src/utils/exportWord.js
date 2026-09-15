import { Document, Packer, Paragraph, TextRun, HeadingLevel, AlignmentType, PageBreak, BorderStyle } from 'docx'
import { saveAs } from 'file-saver'

const typeMap = {
  SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题',
  SHORT_ANSWER: '简答题', BLANK_FILLING: '填空题', PROOF_QUESTION: '证明题',
  MATERIAL: '材料题', ESSAY: '论述题'
}

/**
 * 生成 Word 文档并下载
 * @param {object} paper - 试卷数据（paperName, totalScore, durationMinutes, questions）
 * @param {boolean} withAnswers - 是否包含答案
 */
export async function exportWord(paper, withAnswers) {
  const children = []

  // ===== 标题 =====
  children.push(
    new Paragraph({
      text: paper.paperName || '试卷',
      heading: HeadingLevel.TITLE,
      alignment: AlignmentType.CENTER,
      spacing: { after: 200 }
    })
  )

  // ===== 元信息 =====
  children.push(
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 300 },
      children: [
        new TextRun({ text: `总分：${paper.totalScore} 分   时长：${paper.durationMinutes} 分钟   题数：${paper.questions?.length || 0} 题`, size: 21, color: '444444' })
      ]
    })
  )

  // ===== 分隔线 =====
  children.push(
    new Paragraph({
      spacing: { after: 200 },
      border: { bottom: { style: BorderStyle.SINGLE, size: 6, color: '000000' } }
    })
  )

  // ===== 题目列表 =====
  // 先按 sortOrder 排序，再用真实题号编号 —— 与页面 / PDF 导出的口径保持一致。
  // （原本直接用数组下标 +1，若后端返回顺序不等于 sortOrder，题号就会和页面错位）
  const questions = [...(paper.questions || [])]
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  if (questions.length) {
    questions.forEach((q, index) => {
      // 真实题号；sortOrder 缺失或为 0 时回退到排序后的序号
      const no = q.sortOrder > 0 ? q.sortOrder : index + 1
      const headerText = `${no}、${typeMap[q.questionType] || q.questionType}（${q.score} 分）`
      children.push(
        new Paragraph({
          spacing: { before: 260, after: 80 },
          children: [
            new TextRun({ text: headerText, bold: true, size: 22 })
          ]
        })
      )

      // 题目内容
      children.push(
        new Paragraph({
          spacing: { after: 60 },
          children: [
            new TextRun({ text: q.content, size: 22 })
          ]
        })
      )

      // 选项
      if (q.options && q.options.length) {
        q.options.forEach(opt => {
          children.push(
            new Paragraph({
              indent: { left: 400 },
              spacing: { after: 20 },
              children: [
                new TextRun({ text: opt, size: 21 })
              ]
            })
          )
        })
      }

      // 材料
      if (q.materialText) {
        children.push(
          new Paragraph({
            spacing: { before: 80, after: 40 },
            indent: { left: 200 },
            children: [
              new TextRun({ text: `【材料】${q.materialText}`, size: 20, italics: true, color: '555555' })
            ]
          })
        )
      }

      // 答案与解析（含答案版）
      if (withAnswers && q.answer) {
        children.push(
          new Paragraph({
            spacing: { before: 60 },
            indent: { left: 200 },
            children: [
              new TextRun({ text: `参考答案：${q.answer}`, size: 20, color: '006600' })
            ]
          })
        )
        if (q.analysis) {
          children.push(
            new Paragraph({
              indent: { left: 200 },
              spacing: { after: 40 },
              children: [
                new TextRun({ text: `解析：${q.analysis}`, size: 19, color: '555555' })
              ]
            })
          )
        }
      }
    })
  }

  const doc = new Document({
    styles: {
      default: {
        document: {
          run: {
            font: 'SimSun',
            size: 22
          }
        }
      }
    },
    sections: [{ children }]
  })

  const blob = await Packer.toBlob(doc)
  // 文件名区分含答案版，与 PDF 导出的命名口径对齐（否则同卷导出两份会变成「试卷名(1).docx」，分不清）
  saveAs(blob, `${paper.paperName || '试卷'}${withAnswers ? '（含答案）' : ''}.docx`)
}
