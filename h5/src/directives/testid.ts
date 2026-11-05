import type { Directive } from 'vue'

// `v-testid="'login-email'"` puts data-testid on the native <input>/<textarea> inside a Vant
// component (Vant forwards attrs to its root <div>, but tests need to fill the real control).
// Elements without an inner control get it on the root. Use plain data-testid on buttons/cells.
function apply(el: HTMLElement, value: unknown) {
  if (!value) return
  const id = String(value)
  const target =
    el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement
      ? el
      : (el.querySelector('input, textarea') as HTMLElement | null) ?? el
  if (target.getAttribute('data-testid') !== id) target.setAttribute('data-testid', id)
}

export const vTestid: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    apply(el, binding.value)
  },
  updated(el, binding) {
    apply(el, binding.value)
  },
}
