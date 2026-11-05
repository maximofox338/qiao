import { defineStore } from 'pinia'
import { ref } from 'vue'

/** Login form state kept in memory so opening /legal/:doc from the form and coming back keeps it. */
export const useLoginDraft = defineStore('loginDraft', () => {
  const email = ref('')
  const code = ref('')
  const step = ref<'email' | 'code'>('email')
  const sentAt = ref(0)
  const acceptTerms = ref(false)
  const acceptCrossBorder = ref(false)
  const newUser = ref(false)

  function reset() {
    email.value = ''
    code.value = ''
    step.value = 'email'
    sentAt.value = 0
    acceptTerms.value = false
    acceptCrossBorder.value = false
    newUser.value = false
  }

  return { email, code, step, sentAt, acceptTerms, acceptCrossBorder, newUser, reset }
})
