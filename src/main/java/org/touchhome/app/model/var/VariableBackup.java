package org.touchhome.app.model.var;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(indexes = {@Index(name = "vc", columnList = "vid,created")})
@NoArgsConstructor
public class VariableBackup {

    @Id
    @GeneratedValue
    private Integer id;

    private String vid;

    private long created;

    private String value;

    public VariableBackup(String variableId, WorkspaceVariableMessage message) {
        this.vid = variableId;
        this.created = message.getCreated();
        this.value = message.getValue().toString();
    }
}
