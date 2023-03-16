'use strict'

function register (registry, context) {
  registry.inlineMacro('puml-link', function () {
    this.process((parent, target, attrs) => {
      const anchor = parent.applySubstitutions(`xref:${target}[]`, ['macros'])
      const [_, href, text] = anchor.match(/^<a href="(.+?)"[^>]*>(.*)<\/a>$/)
      const prefix = 'prefix' in attrs ? attrs.prefix : '..'
      const content = `[[${prefix ? [prefix, href].join('/') : href} ${text}]]`
      return this.createInline(parent, 'quoted', content)
    })
  })
}

module.exports.register = register
