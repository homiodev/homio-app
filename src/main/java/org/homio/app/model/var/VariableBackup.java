package org.homio.app.model.var;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
