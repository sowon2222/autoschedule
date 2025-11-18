const fs = require('fs');
const path = require('path');

const src = 'dist';
const dest = '../src/main/resources/static';

if (!fs.existsSync(src)) {
  console.error('❌ dist 폴더가 없습니다. 먼저 npm run build를 실행하세요.');
  process.exit(1);
}

// 기존 파일 정리 (기존 HTML 파일과 js 폴더는 유지)
if (fs.existsSync(dest)) {
  const destFiles = fs.readdirSync(dest);
  const keepFiles = ['login.html', 'signup.html', 'users.html', 'task.html', 'team.html', 'event.html', 'ws-test.html', 'app.js', 'js'];
  
  destFiles.forEach(f => {
    if (!keepFiles.includes(f)) {
      const destPath = path.join(dest, f);
      try {
        const stat = fs.statSync(destPath);
        if (stat.isDirectory()) {
          fs.rmSync(destPath, { recursive: true, force: true });
        } else {
          fs.unlinkSync(destPath);
        }
      } catch (err) {
        console.warn(`⚠️  파일 삭제 실패: ${f}`, err.message);
      }
    }
  });
}

// 새 파일 복사
const files = fs.readdirSync(src);
files.forEach(f => {
  const srcPath = path.join(src, f);
  const destPath = path.join(dest, f);
  
  try {
    const stat = fs.statSync(srcPath);
    if (stat.isDirectory()) {
      // 디렉토리인 경우
      if (fs.existsSync(destPath)) {
        fs.rmSync(destPath, { recursive: true, force: true });
      }
      fs.mkdirSync(destPath, { recursive: true });
      const subFiles = fs.readdirSync(srcPath);
      subFiles.forEach(sf => {
        fs.copyFileSync(path.join(srcPath, sf), path.join(destPath, sf));
      });
    } else {
      // 파일인 경우
      fs.copyFileSync(srcPath, destPath);
    }
  } catch (err) {
    console.error(`❌ 파일 복사 실패: ${f}`, err.message);
    process.exit(1);
  }
});

console.log('✅ 빌드된 파일이 static 폴더로 복사되었습니다!');






