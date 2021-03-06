/*
 * Copyright 2014-15 Intelix Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

define(['react', 'core_mixin', 'common_nodetabs', './Table', './Editor'],
    function (React, core_mixin, Tabs, Table, Editor) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function() { return "app/content/gates/main"; },

            getInitialState: function () {
                return {selected: false}
            },

            subscribeToEvents: function() { return [
                ["addGate", this.openModal],
                ["editGate", this.openEditModal],
                ["gatesModalClosed", this.closeModal],
                ["nodeSelectorForGates", this.handleSelectionEvent]
            ]},

            openModal: function (evt) {
                var defaults = {
                    "name": "",
                    "address": "",
                    "inFlightThreshold": 1000,
                    "noSinkDropMessages": false
                };
                this.setState({editBlock: <Editor addr={this.state.selected} mgrRoute="gates" title="Gate configuration" defaults={defaults}  editorId="gates"/>});
            },
            openEditModal: function (evt) {
                this.setState({editBlock: <Editor addr={this.state.selected} mgrRoute="gates" ckey={evt.detail.ckey} title="Gate configuration"  editorId="gates"/>});
            },
            closeModal: function () {
                this.setState({editBlock: false});
            },

            handleSelectionEvent: function (evt) {
                this.setState({selected: evt.detail.address});
            },


            render: function () {

                return <div>

                    <Tabs roles={["hub"]} selectorId="nodeSelectorForGates" />
                    {this.state.editBlock ? this.state.editBlock : ""}
                    {this.state.selected ? <Table addr={this.state.selected}/> : ""}
                </div>;
            }
        });

    });