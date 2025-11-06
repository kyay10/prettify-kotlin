package io.github.kyay10.prettifykotlin

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.childLeafs
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.lexer.KtTokens

class TokenFoldingBuilder : FoldingBuilderEx(), DumbAware {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> =
    buildList {
      root.childLeafs().forEach { element ->
        AppSettings.instance!!.state.tokenToReplacement[element.elementType]?.let { replacement ->
          add(prettyFoldingDescriptor(element, replacement))
          return@forEach
        }
        if (element.elementType == KtTokens.IDENTIFIER) {
          val replacement = AppSettings.instance!!.state.identifierToReplacement.fold(element.text) { acc, (regex, replacement) ->
            acc.replace(regex, replacement)
          }
          add(prettyFoldingDescriptor(element, replacement))
        }
      }
    }.filterNotNull().toTypedArray()

  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}
