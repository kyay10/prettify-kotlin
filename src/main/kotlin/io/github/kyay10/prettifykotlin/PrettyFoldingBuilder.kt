package io.github.kyay10.prettifykotlin

import arrow.core.raise.impure
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.toUElementOfType

val PRETTY_FQNAME = FqName("io.github.kyay10.prettifykotlin.Pretty")
val PREFIX_FQNAME = FqName("io.github.kyay10.prettifykotlin.Prefix")
val POSTFIX_FQNAME = FqName("io.github.kyay10.prettifykotlin.Postfix")

class PrettyFoldingBuilder : FoldingBuilderEx() {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> =
    if (quick) emptyArray()
    else buildList {
      root.accept(object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) = impure {
          super.visitSimpleNameExpression(expression)

          ensure(expression.parent !is KtThisExpression && expression.parent !is KtSuperExpression && expression !is KtLabelReferenceExpression)
          val reference = expression.mainReference.resolve()
          ensure(reference is KtAnnotated)

          val annotation = reference.findAnnotation(PRETTY_FQNAME).bind()
          val name = annotation.valueArguments.singleOrNull().bind().constValue
          ensure(name is String)

          add(PrettyFoldingDescriptor(expression, name))
        }

        override fun visitCallExpression(expression: KtCallExpression) = impure {
          super.visitCallExpression(expression)

          val reference = expression.calleeExpression.bind().mainReference.bind().resolve()
          ensure(reference is KtAnnotated)

          val annotation = reference.findAnnotation(PREFIX_FQNAME).bind()
          val (prefix, suffix) = annotation.valueArguments.also { ensure(it.size == 2) }.map { it.constValue }
          ensure(prefix is String && suffix is String)

          //val group = FoldingGroup.newGroup("brackets")
          val (leftPar, rightPar) = expression.valueArgumentList.bind().run {
            leftParenthesis.bind() to rightParenthesis.bind()
          }
          add(PrettyFoldingDescriptor(leftPar, prefix))
          add(PrettyFoldingDescriptor(rightPar, suffix))
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) = impure {
          super.visitDotQualifiedExpression(expression)
          val selector = expression.selectorExpression
          ensure(selector is KtSimpleNameExpression)
          val reference = selector.mainReference.bind().resolve()
          ensure(reference is KtAnnotated)

          val annotation = reference.findAnnotation(POSTFIX_FQNAME).bind()
          val suffix = annotation.valueArguments.singleOrNull().bind().constValue
          ensure(suffix is String)
          add(PrettyFoldingDescriptor(expression.allChildren.first { it.text == "." }, suffix))
        }
      })
    }.toTypedArray()


  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}

private val ValueArgument.constValue: Any?
  get() = getArgumentExpression()?.toUElementOfType<UExpression>()?.evaluate()