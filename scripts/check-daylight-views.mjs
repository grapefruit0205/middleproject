// Read-only UI checks with deterministic API fixtures; no DB writes or email.
import assert from 'node:assert/strict';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE);
const browser=await chromium.launch({executablePath:process.env.CHROME_BIN || '/opt/google/chrome/chrome',headless:true,args:['--no-sandbox']});
const context=await browser.newContext({viewport:{width:390,height:844},timezoneId:'Asia/Seoul'});
const page=await context.newPage(), errors=[];
page.on('pageerror',error=>errors.push(error.message));
const fixtures=[
  {id:'demo-1',title:'팀 디자인 리뷰 · 모바일 가시성과 등록 화면 함께 확인',startsAt:'2028-02-29T09:00:00+09:00',leadMinutes:30,status:'SCHEDULED'},
  {id:'demo-2',title:'발표 자료 마무리',startsAt:'2028-02-29T09:00:00+09:00',leadMinutes:60,status:'SCHEDULE_PENDING'},
  {id:'demo-3',title:'월간 계획 회의',startsAt:'2028-03-01T14:00:00+09:00',leadMinutes:60,status:'CANCELLED'}
].map(row=>({...row,remindAt:row.startsAt,version:0,eventVersion:0,policyVersion:0}));
await page.route('**/api/deadlines**',route=>{
  assert.equal(route.request().method(),'GET');
  const path=new URL(route.request().url()).pathname.split('/');
  const data=path.at(-1)==='history' ? {deliveryMode:'DISABLED',deliveryModeDetail:'외부 발송 비활성화',entries:[]} : path.length===3 ? fixtures : fixtures.find(row=>row.id===path.at(-1));
  return route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
});
try {
  await page.clock.install({time:new Date('2028-02-29T08:00:00+09:00')});
  await page.goto('http://127.0.0.1:8088/daylight/');
  await page.locator('#api-status').filter({hasText:'서버 DB 연결'}).waitFor();
  assert.equal(await page.locator('[data-calendar-view=day]').getAttribute('aria-pressed'),'true');
  assert.equal(await page.locator('.schedule .day-column').count(),1);
  assert.equal(await page.locator('.schedule .event').count(),2);
  const cards=await page.locator('.schedule .event').first().boundingBox();
  assert.ok(cards.width>100);
  await page.locator('.schedule .event[data-id="demo-1"]').click();
  await page.locator('#detail-description').filter({hasText:'비활성화'}).waitFor();
  assert.equal(await page.locator('#detail-title').textContent(),fixtures[0].title);
  await page.locator('#close-detail').click();
  await page.screenshot({path:'/tmp/daylight-view-day.png'});
  await page.locator('[data-calendar-view=week]').click();
  assert.equal(await page.locator('.week-header .day-heading').count(),7);
  assert.equal(await page.locator('.schedule .day-column').count(),7);
  assert.equal(await page.locator('.schedule .event').count(),3);
  await page.getByRole('button',{name:'2028-03-01 수요일',exact:true}).click();
  assert.equal(await page.locator('#calendar-day').inputValue(),'1');
  await page.screenshot({path:'/tmp/daylight-view-week.png'});
  await page.locator('[data-calendar-view=month]').click();
  assert.match(await page.locator('#calendar-period').textContent(),/2028년 3월/);
  await page.locator('#previous-week').click();
  assert.match(await page.locator('#calendar-period').textContent(),/2028년 2월/);
  await page.locator('.month-date[data-date="2028-02-29"]').click();
  assert.equal(await page.locator('.month-cell.selected .month-event').count(),2);
  assert.equal(await page.locator('.month-grid .month-date').count(),35);
  await page.screenshot({path:'/tmp/daylight-view-month.png'});
  for (const width of [320,390,430,700,1024,1440]) {
    await page.setViewportSize({width,height:900});
    await page.waitForTimeout(60);
    for (const view of ['day','week','month']) {
      await page.locator(`[data-calendar-view=${view}]`).click();
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,`${width} ${view} page overflow`);
      if (width<=700 && view!=='week') assert.equal(await page.locator('.week-scroll').evaluate(el=>el.scrollWidth<=el.clientWidth),true,`${width} ${view} calendar overflow`);
    }
  }
  // Month movement clamps Jan 31 -> leap Feb 29 and handles year rollover.
  await page.locator('#calendar-year').selectOption('2028');
  await page.locator('#calendar-month').selectOption('1');
  await page.locator('#calendar-day').selectOption('31');
  await page.locator('#next-week').click();
  assert.equal(await page.locator('#calendar-day').inputValue(),'29');
  await page.locator('#calendar-month').selectOption('12');
  await page.locator('#next-week').click();
  assert.equal(await page.locator('#calendar-year').inputValue(),'2029');
  await page.locator('#today-button').click();
  await page.locator('[data-calendar-view=day]').click();
  await page.locator('#search-button').click();
  await page.locator('#event-search').fill('발표');
  assert.equal(await page.locator('.schedule .event:visible').count(),1);
  await page.locator('#event-search').fill('없는 일정');
  assert.match(await page.locator('#calendar-empty h1').textContent(),/조건에 맞는/);
  await page.reload();
  await page.locator('#api-status').filter({hasText:'서버 DB 연결'}).waitFor();
  assert.equal(await page.locator('[data-calendar-view=day]').getAttribute('aria-pressed'),'true');
  assert.deepEqual(errors,[]);
  console.log('PASS: original-style daily/weekly timelines and monthly cards, detail, leap/year navigation, search, persistence, 320–1440 px; mobile week scrolls horizontally');
} finally { await browser.close(); }
