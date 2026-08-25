# spark-udf-lp builder 构建镜像
# 说明：与 dataagent-lp builder 的区别——无前端（不需要 Node/pnpm），
#       且必须使用 JDK8（与集群 spark 容器 openjdk:8-jre-slim 运行时一致）。
# 镜像仅含 JDK8 + Maven，构建时挂载源码 / .m2 缓存卷 / settings.xml。
FROM maven:3.9-eclipse-temurin-8

# 阿里云 apt 镜像源（Debian/Ubuntu 兼容，仅换源，不额外装包；失败不阻塞构建）
RUN sed -i 's@deb.debian.org@mirrors.aliyun.com@g; s@security.debian.org@mirrors.aliyun.com@g' /etc/apt/sources.list 2>/dev/null || true; \
    sed -i 's@archive.ubuntu.com@mirrors.aliyun.com@g; s@security.ubuntu.com@mirrors.aliyun.com@g' /etc/apt/sources.list 2>/dev/null || true; \
    rm -rf /var/lib/apt/lists/* 2>/dev/null || true

WORKDIR /workspace/src

# 约定：
#   源码挂载:  -v <repo>:/workspace/src
#   缓存卷:    -v spark-udf-m2:/workspace/.m2-cache
#   仓库配置:  -v <repo>/builder/settings.xml:/workspace/settings.xml:ro
