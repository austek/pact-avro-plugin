plugins {
    id("com.diffplug.spotless")
}

spotless {
    format("misc") {
        target("**/*.gradle.kts", "**/*.gitignore")
        targetExclude("buildSrc/build/**/*")
        indentWithSpaces()
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("documentation") {
        target("**/*.adoc", "**/*.md")
        targetExclude("**/.gradle/**/*", "**/node_modules/**/*")
        trimTrailingWhitespace()
        endWithNewline()
    }

    pluginManager.withPlugin("java") {
        java {
            toggleOffOn("@formatter:off", "@formatter:on")
            targetExclude("**/generated/**/*")
            // As the method name suggests, bump this number if any of the below "custom" rules change.
            // Spotless will not run on unchanged files unless this number changes.
            bumpThisNumberIfACustomStepChanges(0)

            googleJavaFormat().aosp()
            importOrder("\\#java", "\\#javax", "\\#org", "\\#com", "\\#", "java", "javax", "org", "com", "")

            custom("Remove unhelpful javadoc stubs", { it ->
                // e.g., remove the following lines:
                // "* @param paramName"
                // "* @throws ExceptionType"
                // "* @return returnType"'
                // Multiline to allow anchors on newlines
                it.replace("(?m)^ *\\* *@(?:param|throws|return) *\\w* *\\n".toRegex(), "")
            })

            custom("Remove author javadoc stubs", { it ->
                // Multiline to allow anchors on newlines
                it.replace("(?m)^ *\\* @author *.* *\\n".toRegex(), "")
            })

            custom("Remove any empty Javadocs and block comments", { it ->
                // Matches any /** [...] */ or /* [...] */ that contains:
                // (a) only whitespace
                // (b) trivial information, such as "@param paramName" or @throws ExceptionType
                //     without any additional information.  This information is implicit in the signature.
                it.replace("\\/\\*+\\s*\\n?(\\s*\\*\\s*\\n)*\\s*\\*+\\/\\s*\\n".toRegex(), "")
            })
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
