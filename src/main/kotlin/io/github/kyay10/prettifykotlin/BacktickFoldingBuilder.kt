package io.github.kyay10.prettifykotlin

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class BacktickFoldingBuilder : FoldingBuilderEx(), DumbAware {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> =
    buildList {
      root.accept(object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
          super.visitSimpleNameExpression(expression)
          if (expression.text.startsWith('`') && expression.text.endsWith('`')) {
            val name = expression.text.substring(1, expression.text.length - 1)
            add(prettyFoldingDescriptor(expression, name))
          }
        }
      })
    }.toTypedArray()

  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}
