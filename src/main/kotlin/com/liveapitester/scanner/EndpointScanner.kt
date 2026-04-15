package com.liveapitester.scanner

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.JavaPsiFacade
import com.liveapitester.http.HttpMethod

data class EndpointInfo(
    val httpMethod: HttpMethod,
    val path: String,
    val className: String,
    val methodName: String,
    val description: String = ""
)

class EndpointScanner(private val project: Project) {

    private val springMappingAnnotations = mapOf(
        "org.springframework.web.bind.annotation.GetMapping" to HttpMethod.GET,
        "org.springframework.web.bind.annotation.PostMapping" to HttpMethod.POST,
        "org.springframework.web.bind.annotation.PutMapping" to HttpMethod.PUT,
        "org.springframework.web.bind.annotation.DeleteMapping" to HttpMethod.DELETE,
        "org.springframework.web.bind.annotation.PatchMapping" to HttpMethod.PATCH,
        "org.springframework.web.bind.annotation.RequestMapping" to HttpMethod.GET
    )

    private val jaxRsAnnotations = mapOf(
        "javax.ws.rs.GET" to HttpMethod.GET,
        "javax.ws.rs.POST" to HttpMethod.POST,
        "javax.ws.rs.PUT" to HttpMethod.PUT,
        "javax.ws.rs.DELETE" to HttpMethod.DELETE,
        "javax.ws.rs.PATCH" to HttpMethod.PATCH,
        "jakarta.ws.rs.GET" to HttpMethod.GET,
        "jakarta.ws.rs.POST" to HttpMethod.POST,
        "jakarta.ws.rs.PUT" to HttpMethod.PUT,
        "jakarta.ws.rs.DELETE" to HttpMethod.DELETE,
        "jakarta.ws.rs.PATCH" to HttpMethod.PATCH
    )

    fun scanProject(): List<EndpointInfo> {
        val results = mutableListOf<EndpointInfo>()
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        results.addAll(scanSpringAnnotations(facade, scope))
        results.addAll(scanJaxRsAnnotations(facade, scope))

        return results.sortedWith(compareBy({ it.path }, { it.httpMethod.name }))
    }

    private fun scanSpringAnnotations(
        facade: JavaPsiFacade,
        scope: GlobalSearchScope
    ): List<EndpointInfo> {
        val results = mutableListOf<EndpointInfo>()

        for ((annotationFqn, defaultMethod) in springMappingAnnotations) {
            val annotationClass = facade.findClass(annotationFqn, GlobalSearchScope.allScope(project))
                ?: continue

            val methods = AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope)
            for (psiMethod in methods) {
                val annotation = psiMethod.getAnnotation(annotationFqn) ?: continue
                val classPath = getClassPath(psiMethod)
                val methodPath = getAnnotationPath(annotation)
                val fullPath = normalizePath("$classPath$methodPath")

                val httpMethod = if (annotationFqn.endsWith("RequestMapping")) {
                    getRequestMappingMethod(annotation) ?: defaultMethod
                } else {
                    defaultMethod
                }

                val className = (psiMethod.containingClass?.qualifiedName ?: psiMethod.containingClass?.name) ?: "Unknown"

                results.add(EndpointInfo(
                    httpMethod = httpMethod,
                    path = fullPath,
                    className = className,
                    methodName = psiMethod.name,
                    description = "$className#${psiMethod.name}"
                ))
            }
        }
        return results
    }

    private fun scanJaxRsAnnotations(
        facade: JavaPsiFacade,
        scope: GlobalSearchScope
    ): List<EndpointInfo> {
        val results = mutableListOf<EndpointInfo>()

        val pathAnnotations = listOf(
            "javax.ws.rs.Path",
            "jakarta.ws.rs.Path"
        )

        for ((annotationFqn, httpMethod) in jaxRsAnnotations) {
            val annotationClass = facade.findClass(annotationFqn, GlobalSearchScope.allScope(project))
                ?: continue

            val methods = AnnotatedElementsSearch.searchPsiMethods(annotationClass, scope)
            for (psiMethod in methods) {
                val classPath = getJaxRsClassPath(psiMethod, pathAnnotations)
                val methodPath = getJaxRsMethodPath(psiMethod, pathAnnotations)
                val fullPath = normalizePath("$classPath$methodPath")

                val className = (psiMethod.containingClass?.qualifiedName ?: psiMethod.containingClass?.name) ?: "Unknown"

                results.add(EndpointInfo(
                    httpMethod = httpMethod,
                    path = fullPath,
                    className = className,
                    methodName = psiMethod.name,
                    description = "$className#${psiMethod.name}"
                ))
            }
        }
        return results
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
            value is PsiArrayInitializerMemberValue -> {
                value.initializers.firstOrNull()?.let { extractStringValue(it) } ?: ""
            }
            else -> extractStringValue(value)
        }
    }

    private fun getRequestMappingMethod(annotation: PsiAnnotation): HttpMethod? {
        val methodAttr = annotation.findAttributeValue("method") ?: return null
        val methodStr = methodAttr.text
        return when {
            methodStr.contains("GET") -> HttpMethod.GET
            methodStr.contains("POST") -> HttpMethod.POST
            methodStr.contains("PUT") -> HttpMethod.PUT
            methodStr.contains("DELETE") -> HttpMethod.DELETE
            methodStr.contains("PATCH") -> HttpMethod.PATCH
            methodStr.contains("HEAD") -> HttpMethod.HEAD
            methodStr.contains("OPTIONS") -> HttpMethod.OPTIONS
            else -> null
        }
    }

    private fun getJaxRsClassPath(psiMethod: PsiMethod, pathAnnotations: List<String>): String {
        val containingClass = psiMethod.containingClass ?: return ""
        for (ann in pathAnnotations) {
            val annotation = containingClass.getAnnotation(ann) ?: continue
            return getAnnotationPath(annotation)
        }
        return ""
    }

    private fun getJaxRsMethodPath(psiMethod: PsiMethod, pathAnnotations: List<String>): String {
        for (ann in pathAnnotations) {
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
