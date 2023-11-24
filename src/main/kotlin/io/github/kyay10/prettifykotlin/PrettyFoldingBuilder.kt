package io.github.kyay10.prettifykotlin

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.inspections.AbstractRangeInspection.Companion.constantValueOrNull
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*

val PRETTY_FQNAME = FqName("io.github.kyay10.prettifykotlin.Pretty")

class PrettyFoldingBuilder : FoldingBuilderEx() {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> =
    if (quick) emptyArray()
    else buildList {
      root.accept(object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
          super.visitSimpleNameExpression(expression)
          if (expression.parent is KtThisExpression || expression.parent is KtSuperExpression || expression is KtLabelReferenceExpression) return
          val reference = expression.mainReference.resolve() as? KtAnnotated ?: return
          val annotation = reference.findAnnotation(PRETTY_FQNAME) ?: return
          val name = annotation.valueArguments.singleOrNull()?.constValue as? String ?: return
          add(PrettyFoldingDescriptor(expression, name))
        }
      })
    }.toTypedArray()


  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}

private val ValueArgument.constValue get() = getArgumentExpression()?.constantValueOrNull()?.value