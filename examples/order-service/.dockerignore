# 构建上下文只需要已构建的可执行 jar，其余内容不进入上下文。
# 注意：Docker 要求逐级取消忽略，父目录被忽略时子路径无法重新包含。
*
!bootstrap
bootstrap/*
!bootstrap/build
bootstrap/build/*
!bootstrap/build/libs
bootstrap/build/libs/*
!bootstrap/build/libs/bootstrap.jar
