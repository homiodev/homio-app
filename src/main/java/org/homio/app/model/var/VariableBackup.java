package org.homio.app.model.var;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

@Getter
@Setter
@Entity
@Table(indexes = {@Index(name = "vc", columnList = "vid,created")})
@NoArgsConstructor
public class VariableBackup {

    @Id
    @Column(length = 64, nullable = false, unique = true)
    @GeneratedValue(generator = "id-generator")
    @GenericGenerator(name = "id-generator", strategy = "org.homio.app.repository.HomioIdGenerator")
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
