package com.liveapitester.debugger

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.JavaPsiFacade

data class ResolvedEndpoint(
    val psiMethod: PsiMethod,
    val psiClass: PsiClass,
    val filePath: String,
    val lineNumber: Int
)

class EndpointMethodResolver(private val project: Project) {

    private val springMappingAnnotations = mapOf(
        "org.springframework.web.bind.annotation.GetMapping" to "GET",
        "org.springframework.web.bind.annotation.PostMapping" to "POST",
        "org.springframework.web.bind.annotation.PutMapping" to "PUT",
        "org.springframework.web.bind.annotation.DeleteMapping" to "DELETE",
        "org.springframework.web.bind.annotation.PatchMapping" to "PATCH",
        "org.springframework.web.bind.annotation.RequestMapping" to null
    )

    private val jaxRsAnnotations = listOf(
        "javax.ws.rs.GET", "javax.ws.rs.POST", "javax.ws.rs.PUT",
        "javax.ws.rs.DELETE", "javax.ws.rs.PATCH",
        "jakarta.ws.rs.GET", "jakarta.ws.rs.POST", "jakarta.ws.rs.PUT",
        "jakarta.ws.rs.DELETE", "jakarta.ws.rs.PATCH"
    )

    fun resolve(urlPath: String, httpMethod: String): ResolvedEndpoint? {
        return ReadAction.compute<ResolvedEndpoint?, Exception> {
            resolveInternal(urlPath, httpMethod)
        }
    }

    private fun resolveInternal(urlPath: String, httpMethod: String): ResolvedEndpoint? {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val normalizedPath = normalizePath(urlPath)

        // Try Spring annotations first
        for ((annotationFqn, defaultHttpMethod) in springMappingAnnotations) {
            val annotationClass = facade.findClass(annotationFqn, GlobalSearchScope.allScope(project))
                ?: continue
            val methods = AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope)
            for (psiMethod in methods) {
                val annotation = psiMethod.getAnnotation(annotationFqn) ?: continue
                val classPath = getClassPath(psiMethod)
                val methodPath = getAnnotationPath(annotation)
                val fullPath = normalizePath("$classPath$methodPath")

                val resolvedHttpMethod = if (annotationFqn.endsWith("RequestMapping")) {
                    getRequestMappingMethod(annotation) ?: "GET"
                } else {
                    defaultHttpMethod ?: "GET"
                }

                if (pathMatches(normalizedPath, fullPath) &&
                    resolvedHttpMethod.equals(httpMethod, ignoreCase = true)) {
                    return@resolveInternal buildResolvedEndpoint(psiMethod)
                }
            }
        }

        // Try JAX-RS annotations
        for (annotationFqn in jaxRsAnnotations) {
            val annotationClass = facade.findClass(annotationFqn, GlobalSearchScope.allScope(project))
                ?: continue
            val methods = AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope)
            for (psiMethod in methods) {
                val classPath = getJaxRsClassPath(psiMethod)
                val methodPath = getJaxRsMethodPath(psiMethod)
                val fullPath = normalizePath("$classPath$methodPath")
                val resolvedMethod = annotationFqn.substringAfterLast(".")
                if (pathMatches(normalizedPath, fullPath) &&
                    resolvedMethod.equals(httpMethod, ignoreCase = true)) {
                    return@resolveInternal buildResolvedEndpoint(psiMethod)
                }
            }
        }

        return null
    }

    private fun buildResolvedEndpoint(psiMethod: PsiMethod): ResolvedEndpoint? {
        val containingClass = psiMethod.containingClass ?: return null
        val containingFile = psiMethod.containingFile ?: return null
        val virtualFile = containingFile.virtualFile ?: return null
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
            .getDocument(virtualFile) ?: return null
        val lineNumber = document.getLineNumber(psiMethod.textOffset)
        return ResolvedEndpoint(
            psiMethod = psiMethod,
            psiClass = containingClass,
            filePath = virtualFile.path,
            lineNumber = lineNumber
        )
    }

    private fun pathMatches(requestPath: String, endpointPattern: String): Boolean {
        // Strip query string from request path
        val cleanRequestPath = requestPath.substringBefore("?")

        // Direct match
        if (cleanRequestPath.equals(endpointPattern, ignoreCase = true)) return true

        // Template match: /api/users/{id} matches /api/users/123
        val patternParts = endpointPattern.split("/")
        val requestParts = cleanRequestPath.split("/")
        if (patternParts.size != requestParts.size) return false

        return patternParts.zip(requestParts).all { (pattern, request) ->
            pattern.startsWith("{") && pattern.endsWith("}") || pattern.equals(request, ignoreCase = true)
        }
    }

    private fun getClassPath(psiMethod: PsiMethod): String {
        val containingClass = psiMethod.containingClass ?: return ""
        val annotation = containingClass.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
            ?: return ""
        return getAnnotationPath(annotation)
    }

    private fun getAnnotationPath(annotation: PsiAnnotation): String {
        val value = annotation.findAttributeValue("value")
            ?: annotation.findAttributeValue("path")
            ?: return ""
        return when {
            value is PsiArrayInitializerMemberValue ->
                value.initializers.firstOrNull()?.let { extractStringValue(it) } ?: ""
            else -> extractStringValue(value)
        }
    }

    private fun getRequestMappingMethod(annotation: PsiAnnotation): String? {
        val methodAttr = annotation.findAttributeValue("method") ?: return null
        val methodStr = methodAttr.text
        return when {
            methodStr.contains("GET") -> "GET"
            methodStr.contains("POST") -> "POST"
            methodStr.contains("PUT") -> "PUT"
            methodStr.contains("DELETE") -> "DELETE"
            methodStr.contains("PATCH") -> "PATCH"
            methodStr.contains("HEAD") -> "HEAD"
            methodStr.contains("OPTIONS") -> "OPTIONS"
            else -> null
        }
    }

    private fun getJaxRsClassPath(psiMethod: PsiMethod): String {
        val containingClass = psiMethod.containingClass ?: return ""
        for (ann in listOf("javax.ws.rs.Path", "jakarta.ws.rs.Path")) {
            val annotation = containingClass.getAnnotation(ann) ?: continue
            return getAnnotationPath(annotation)
        }
        return ""
    }

    private fun getJaxRsMethodPath(psiMethod: PsiMethod): String {
        for (ann in listOf("javax.ws.rs.Path", "jakarta.ws.rs.Path")) {
            val annotation = psiMethod.getAnnotation(ann) ?: continue
            return getAnnotationPath(annotation)
        }
        return ""
    }

    private fun extractStringValue(element: PsiElement): String {
        return when (element) {
            is PsiLiteralExpression -> element.value?.toString() ?: ""
            else -> element.text.trim('"', '\'', ' ')
        }
    }

    private fun normalizePath(path: String): String {
        var result = path.replace("//", "/")
        if (!result.startsWith("/")) result = "/$result"
        return result
    }
}
