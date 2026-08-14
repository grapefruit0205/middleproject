package com.middleproject.reminder.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.middleproject.reminder.domain.Event;
import com.middleproject.reminder.domain.Reminder;
import com.middleproject.reminder.domain.ReminderStatus;
import com.middleproject.reminder.port.ReminderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReminderService {
    private final ReminderRepository repository;
    private final IdempotencyService idempotency;
    private final JdbcTemplate db;
    private final ObjectMapper objectMapper;
    public ReminderService(ReminderRepository repository, IdempotencyService idempotency, JdbcTemplate db, ObjectMapper objectMapper) { this.repository=repository; this.idempotency=idempotency; this.db=db; this.objectMapper=objectMapper; }
    public List<Reminder> all() { return repository.findAll(); }
    public List<Reminder> all(String ownerId) { return repository.findAllByOwner(ownerId); }
    public Reminder find(UUID id) { return repository.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Reminder not found")); }
    public Reminder find(UUID id,String ownerId) { return repository.findByIdForOwner(id,ownerId).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Reminder not found")); }
    @Transactional public Reminder create(UUID e,UUID p,String k) { return create(e,p,k,null); }
    @Transactional public Reminder create(UUID e,UUID p,String k,String owner) { return idempotency.execute(owner==null?"reminders:create":"reminders:create:"+owner,k,new Object[]{e,p},Reminder.class,()->{ try { Reminder r=repository.insert(UUID.randomUUID(),e,p,owner); enqueue(r.id(),"UPSERT",0,1); return r; } catch(DataIntegrityViolationException x){throw error(HttpStatus.BAD_REQUEST,"eventId or policyId does not exist",x);} }); }
    @Transactional public Reminder update(UUID id,UUID e,UUID p,long v,String k) { return update(id,e,p,v,k,null); }
    @Transactional public Reminder update(UUID id,UUID e,UUID p,long v,String k,String owner) { return idempotency.execute("reminders:update:"+id+(owner==null?"":":"+owner),k,new Object[]{e,p,v},Reminder.class,()->{try { if(!(owner==null?repository.update(id,e,p,v):repository.updateForOwner(id,e,p,v,owner))){if(owner==null)find(id);else find(id,owner);throw conflict();} Reminder r=owner==null?find(id):find(id,owner);enqueue(id,"UPSERT",r.version(),r.version()+1);return r;}catch(DataIntegrityViolationException x){throw error(HttpStatus.BAD_REQUEST,"eventId or policyId does not exist",x);}}); }
    @Transactional public Reminder transition(UUID id,ReminderStatus target,long v,String k){return transition(id,target,v,k,null);}
    @Transactional public Reminder transition(UUID id,ReminderStatus target,long v,String k,String owner){return idempotency.execute("reminders:transition:"+id+(owner==null?"":":"+owner),k,new Object[]{target,v},Reminder.class,()->{Reminder c=owner==null?find(id):find(id,owner);if(c.version()!=v)throw conflict();try{c.transitionTo(target);}catch(IllegalStateException x){throw error(HttpStatus.CONFLICT,x.getMessage(),x);}if(!(owner==null?repository.transition(id,c.status(),target,v):repository.transitionForOwner(id,c.status(),target,v,owner)))throw conflict();Reminder updated=owner==null?find(id):find(id,owner);enqueue(id,target==ReminderStatus.CANCELLED?"DELETE":"UPSERT",updated.version(),target==ReminderStatus.CANCELLED?updated.version():updated.version()+1);return updated;});}
    @Transactional public void delete(UUID id,long v,String k){idempotency.executeVoid("reminders:delete:"+id,k,v,()->{find(id);if(!repository.delete(id,v))throw conflict();enqueue(id,"DELETE",v,v);});}
    private void enqueue(UUID id,String op,long ev,long sv){try{String payload=objectMapper.writeValueAsString(Map.of("reminderId",id.toString(),"schedulerVersion",sv,"idempotencyKey",id+":"+sv));OffsetDateTime due=OffsetDateTime.now();if("UPSERT".equals(op)){Event e=db.queryForObject("select starts_at from events where id=(select event_id from reminders where id=?)",(r,n)->new Event(id,"",r.getObject(1,OffsetDateTime.class),null,0),id);int lead=db.queryForObject("select lead_minutes from notification_policies where id=(select policy_id from reminders where id=?)",Integer.class,id);due=e.startsAt().minusMinutes(lead);}db.update("insert into schedule_outbox(id,reminder_id,operation,expected_version,scheduler_version,due_at,payload) values(?,?,?,?,?,?,?)",UUID.randomUUID(),id,op,ev,sv,due,payload);}catch(JsonProcessingException x){throw new IllegalStateException(x);}}
    private ResponseStatusException conflict(){return error(HttpStatus.CONFLICT,"Optimistic lock conflict");} private ResponseStatusException error(HttpStatus s,String m){return new ResponseStatusException(s,m);} private ResponseStatusException error(HttpStatus s,String m,Throwable x){return new ResponseStatusException(s,m,x);}
}
