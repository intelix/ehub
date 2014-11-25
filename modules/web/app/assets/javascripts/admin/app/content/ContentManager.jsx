/*
 * Copyright 2014 Intelix Pty Ltd
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

define(
    ['react',
        'coreMixin',
        'app_gates',
        'app_flows',
        'app_agents',
        'app_notif'],
    function (React,
              coreMixin,
              Gates,
              Flows,
              Agents,
              Notif) {

        return React.createClass({
            mixins: [coreMixin],

            getInitialState: function () {
                return { selection: "gates" }
            },

            onMount: function() {
                this.addEventListener("navBarSelection", this.handleSelectionEvent);
            },

            onUnmount: function() {
                this.removeEventListener("navBarSelection", this.handleSelectionEvent);
            },

            handleSelectionEvent: function(evt) {
                this.setState({selection: evt.detail.id});
            },

            render: function () {

                var selection = false;

                switch (this.state.selection) {
                    case 'gates': selection = <Gates />; break;
                    case 'flows': selection = <Flows />; break;
                    case 'agents': selection = <Agents />; break;
                    case 'notif': selection = <Notif />; break;
                    default: selection = "";
                }

                return <div className="container main-container" role="main"><span>{selection}</span></div>
            }
        });

    });