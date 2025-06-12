# 현재 스크립트 파일이 있는 디렉토리 경로 가져오기
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Definition

# 백업 디렉토리 설정 (스크립트 경로 기준 상대경로)
$backupDirectory = Join-Path $scriptDirectory "mysql-backup"

# 사용자 입력 받기
$containerName = Read-Host "도커 컨테이너 이름"
$databaseName = Read-Host "백업할 데이터베이스 이름"
$username = Read-Host "사용자 이름"
$password = Read-Host "비밀번호"

# 날짜 형식
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"

# 백업 파일 경로
$backupFile = "$backupDirectory\$databaseName-$timestamp.sql"

# 백업 디렉토리가 없으면 생성
if (!(Test-Path -Path $backupDirectory)) {
    New-Item -ItemType Directory -Path $backupDirectory | Out-Null
}

# 도커 mysqldump 실행
docker exec $containerName sh -c "exec mysqldump -u $username -p$password $databaseName" > $backupFile

Write-Host "✅ 백업 완료: $backupFile"