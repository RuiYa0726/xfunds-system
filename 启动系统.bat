@echo off
chcp 65001 >nul
echo ========================================
echo 银行外汇交易系统 - 一键启动
echo ========================================
echo.

REM 检查Java
echo [1/3] 检查Java环境...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未找到Java，请先安装Java 17或更高版本！
    pause
    exit /b 1
)
echo [成功] Java环境检查通过！
echo.

REM 检查MySQL
echo [2/3] 检查MySQL数据库...
mysql -h127.0.0.1 -uroot -paa111111 -e"SELECT 1" >nul 2>&1
if %errorlevel% neq 0 (
    echo [警告] 无法连接MySQL，请确保MySQL正在运行且密码为 aa111111
    echo.
    echo 尝试继续启动服务...
) else (
    echo [成功] MySQL连接成功！
)
echo.

REM 启动后端服务
echo [3/3] 启动系统...
echo 正在启动银行外汇交易系统...
echo 服务地址: http://localhost:8080
echo 登录账号: admin / admin123
echo.
echo ========================================
echo 按 Ctrl+C 停止服务
echo ========================================
echo.

cd /d "%~dp0xfunds-server"
java -jar target\xfunds-server-1.0.0.jar

pause
