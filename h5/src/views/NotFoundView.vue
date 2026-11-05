<template>
  <div class="q-page" data-testid="not-found">
    <van-empty image="search" :description="$t('notFound.title')">
      <van-button round type="primary" to="/" data-testid="not-found-home">{{ $t('notFound.action') }}</van-button>
    </van-empty>
  </div>
</template>
