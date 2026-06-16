# WorkTrace Git 工作流

## 分支策略

```
dev  ← 日常开发分支，所有功能先提交到这里
 │
 └──→ main  ← 稳定版本，定期从 dev 合并
```

## 全流程命令

### 1. 提交到 dev

```bash
cd e:/opc/WorkTrace

# 查看变更
git status

# 暂存所有变更
git add -A

# 查看暂存内容
git diff --cached --stat

# 提交
git commit -m "feat: 简短描述本次变更"

# 推送到远程 dev
git push origin dev
```

### 2. 合并到 main

```bash
# 切换到 main
git checkout main

# 合并 dev（通常 fast-forward）
git merge dev

# 推送到远程 main
git push origin main

# 切回 dev 继续开发
git checkout dev
```

### 3. 一键执行（合并写法）

```bash
cd e:/opc/WorkTrace
git add -A && git commit -m "feat: 描述" && git push origin dev && git checkout main && git merge dev && git push origin main && git checkout dev
```

## 常见场景

### 只提交不合并

```bash
git add -A
git commit -m "fix: 修复xxx"
git push origin dev
```

### 只合并不提交新代码

```bash
git checkout main
git merge dev
git push origin main
git checkout dev
```

### 查看提交历史

```bash
git log --oneline -10
```

### 撤销未提交的修改

```bash
# 撤销单个文件
git checkout -- src/main/java/xxx.java

# 撤销全部
git checkout -- .
```

### 查看远程状态

```bash
git remote -v
git branch -a
```
