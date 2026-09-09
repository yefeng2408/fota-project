<template>
  <footer class="site-footer" aria-label="网站备案信息">
    <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
      沪ICP备2026043045号-1
    </a>
  </footer>
</template>

<style scoped>
.site-footer {
  flex-shrink: 0;
  padding: 16px 24px;
  border-top: 1px solid var(--line);
  background: var(--panel);
  text-align: center;
  font-size: 13px;
  line-height: 1.6;
}

.site-footer a {
  color: #52627a;
  text-decoration: none;
}

.site-footer a:hover {
  color: var(--text-main);
  text-decoration: underline;
}

.site-footer a:focus-visible {
  outline: 2px solid var(--text-main);
  outline-offset: 4px;
  border-radius: 2px;
}
</style>
