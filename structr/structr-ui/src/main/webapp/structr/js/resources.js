/*
 *  Copyright (C) 2010-2012 Axel Morgner, structr <structr@structr.org>
 *
 *  This file is part of structr <http://structr.org>.
 *
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */

var resources;
var previews, previewTabs, controls, palette;
var selStart, selEnd;
var sel;
var contentSourceId, elementSourceId, rootId;
var textBeforeEditing;
var win = $(window);

$(document).ready(function() {
    Structr.registerModule('resources', _Resources);
    Structr.classes.push('resource');

    win.resize(function() {
        _Resources.resize();
    });

});

var _Resources = {

    icon : 'icon/page.png',
    add_icon : 'icon/page_add.png',
    delete_icon : 'icon/page_delete.png',
    clone_icon : 'icon/page_copy.png',

    init : function() {
    },

    resize : function() {

        var windowWidth = win.width();
        var windowHeight = win.height();
        var headerOffsetHeight = 76;
        var previewOffset = 27;
	
        if (resources && palette) {
            
            resources.css({
                width: Math.max(180, Math.min(windowWidth/3, 360)) + 'px',
                height: windowHeight - headerOffsetHeight + 'px'
            });

            var rw = resources.width() + 12;

            palette.css({
                width: Math.min(240, Math.max(360, windowWidth/4)) + 'px',
                height: windowHeight - (headerOffsetHeight+10) + 'px'
            });

            var pw = palette.width() + 60;

            if (previews) previews.css({
                width: windowWidth-rw-pw + 'px',
                height: win.height() - headerOffsetHeight + 'px'
            });

            $('.previewBox', previews).css({
                width: windowWidth-rw-pw-4 + 'px',
                height: windowHeight - (headerOffsetHeight + previewOffset) + 'px'
            });

            $('.previewBox', previews).find('iframe').css({
                width: $('.previewBox', previews).width() + 'px',
                height: windowHeight - (headerOffsetHeight + previewOffset) + 'px'
            });
        }

    },

    onload : function() {
        activeTab = $.cookie('structrActiveTab');
        if (debug) console.log('value read from cookie', activeTab);

        //Structr.activateMenuEntry('resources');
        if (debug) console.log('onload');
        //main.append('<div id="resources"></div><div id="previews"></div><div id="palette"></div><div id="components"></div><div id="elements"></div><div id="contents"></div>');
        
        main.prepend('<div id="resources"></div><div id="previews"></div><div id="palette"></div>');

        resources = $('#resources');
        //components = $('#components');
        //elements = $('#elements');
        //contents = $('#contents');
        previews = $('#previews');
        palette = $('#palette');
        //main.before('<div id="hoverStatus">Hover status</div>');
        $('#controls', main).remove();
        //main.children().first().before('<div id="controls"><input type="checkbox" id="toggleResources">Show Resource Tree <input type="checkbox" id="toggleComponents">Show Components <input type="checkbox" id="toggleElements">Show Elements <input type="checkbox" id="toggleContents">Show Contents</div>');

        previews.append('<ul id="previewTabs"></ul>');
        previewTabs = $('#previewTabs', previews);

        _Resources.refresh();
        //_Resources.refreshComponents();
        //_Resources.refreshElements();
        _Elements.showPalette();
        //_Contents.refresh();

        previewTabs.append('<li id="import_page" class="button"><img class="add_button icon" src="icon/page_white_put.png"></li>');
        $('#import_page', previewTabs).on('click', function(e) {
            e.stopPropagation();
			
            var dialog = $('#dialogBox .dialogText');
            var dialogMsg = $('#dialogMsg');
			
            dialog.empty();
            dialogMsg.empty();

            dialog.append('<table class="props">'
                + '<tr><td><label for="address">Address:</label></td><td><input id="_address" name="address" size="20" value="http://"></td></tr>'
                + '<tr><td><label for="name">Name of new page:</label></td><td><input id="_name" name="name" size="20"></td></tr>'
                + '<tr><td><label for="timeout">Timeout (ms)</label></td><td><input id="_timeout" name="timeout" size="20" value="5000"></td></tr>'
                + '<tr><td><label for="publicVisibilty">Visible to public</label></td><td><input type="checkbox" id="_publicVisible" name="publicVisibility"></td></tr>'
                + '<tr><td><label for="authVisibilty">Visible to authenticated users</label></td><td><input type="checkbox" checked="checked" id="_authVisible" name="authVisibilty"></td></tr>'
                + '</table>');

            var addressField = $('#_address', dialog);

            if (debug) console.log('addressField', addressField);

            addressField.on('blur', function() {
                var addr = $(this).val().replace(/\/+$/, "");
                if (debug) console.log(addr);
                $('#_name', dialog).val(addr.substring(addr.lastIndexOf("/")+1));
            });

            dialog.append('<button id="startImport">Start Import</button>');

            Structr.dialog('Import Page from URL', function() {
                return true;
            }, function() {
                return true;
            });
			
            $('#startImport').on('click', function(e) {
                e.stopPropagation();

                var address = $('#_address', dialog).val();
                var name    = $('#_name', dialog).val();
                var timeout = $('#_timeout', dialog).val();
                var publicVisible = $('#_publicVisible:checked', dialog).val() == 'on';
                var authVisible = $('#_authVisible:checked', dialog).val() == 'on';

                if (debug) console.log('start');
                return Command.importPage(address, name, timeout, publicVisible, authVisible);
            });
            
        });

        previewTabs.append('<li id="add_resource" class="button"><img class="add_button icon" src="icon/add.png"></li>');
        $('#add_resource', previewTabs).on('click', function(e) {
            e.stopPropagation();
            var entity = {};
            entity.type = 'Resource';
            Command.create(entity);
        });

    },

    refresh : function() {
        resources.empty();
        return Command.list('Resource');
    },

    refreshComponents : function() {
        components.empty();
        if (Command.list('Component')) {
            components.append('<button class="add_component_icon button"><img title="Add Component" alt="Add Component" src="' + _Components.add_icon + '"> Add Component</button>');
            $('.add_component_icon', main).on('click', function(e) {
                e.stopPropagation();
                var entity = {};
                entity.type = 'Component';
                Command.create(entity);
            });
        }
    },

    refreshElements : function() {
        elements.empty();
        if (Command.list('Element')) {
            elements.append('<button class="add_element_icon button"><img title="Add Element" alt="Add Element" src="' + _Elements.add_icon + '"> Add Element</button>');
            $('.add_element_icon', main).on('click', function(e) {
                e.stopPropagation();
                var entity = {};
                entity.type = 'Element';
                Command.create(entity);
            });
        }
    },

    addTab : function(entity) {
        previewTabs.children().last().before(''
            + '<li id="show_' + entity.id + '" class="' + entity.id + '_"></li>');

        var tab = $('#show_' + entity.id, previews);
		
        tab.append('<img class="typeIcon" src="icon/page.png"> <b class="name_">' + entity.name + '</b>');
        tab.append('<img title="Delete resource \'' + entity.name + '\'" alt="Delete resource \'' + entity.name + '\'" class="delete_icon button" src="' + Structr.delete_icon + '">');
        tab.append('<img class="view_icon button" title="View ' + entity.name + ' in new window" alt="View ' + entity.name + ' in new window" src="icon/eye.png">');

        $('.view_icon', tab).on('click', function(e) {
            e.stopPropagation();
            var self = $(this);
            var name = $(self.parent().children('b.name_')[0]).text();
            window.open(viewRootUrl + name);
        });

        var deleteIcon = $('.delete_icon', tab);
        deleteIcon.hide();
        deleteIcon.on('click', function(e) {
            e.stopPropagation();
            _Entities.deleteNode(this, entity);
        });
        deleteIcon.on('mouseover', function(e) {
            var self = $(this);
            self.show();
		
        });

        _Entities.appendAccessControlIcon(tab, entity, true);
        
        return tab;
    },

    resetTab : function(element, name) {

        if (debug) console.log('resetTab', element);
        
        element.children('input').hide();
        element.children('.name_').show();
        
        var icons = $('.button', element);
        //icon.hide();

        element.hover(function(e) {
            icons.show();
        },
        function(e) {
            icons.hide();
        });

        element.on('click', function(e) {
            e.stopPropagation();
            var self = $(this);
            var clicks = e.originalEvent.detail;
            if (clicks == 1) {
                if (debug) console.log('click', self, self.css('z-index'));
                if (self.hasClass('active')) {
                    _Resources.makeTabEditable(self);
                } else {
                    _Resources.activateTab(self);
                }
            }
        });

        if (getId(element) == activeTab) {
            _Resources.activateTab(element);
        }
    },

    activateTab : function(element) {
        
        var name = $.trim(element.children('b.name_').text());
        if (debug) console.log('activateTab', element, name);

        previewTabs.children('li').each(function() {
            $(this).removeClass('active');
        });

        $('.previewBox', previews).each(function() {
            $(this).hide();
        });
        //var id = $(this).attr('id').substring(5);

        var id = getId(element);
        activeTab = id;

        _Resources.reloadIframe(id, name);

        element.addClass('active');

        if (debug) console.log('set cookie for active tab', activeTab);
        $.cookie('structrActiveTab', activeTab, {
            expires: 7,
            path: '/'
        });

    },
    
    reloadIframe : function(id, name) {
        var iframe = $('#preview_' + id);
        if (debug) console.log(iframe);
        iframe.attr('src', viewRootUrl + name + '?edit');
        iframe.parent().show();
        iframe.on('load', function() {
            if (debug) console.log('iframe loaded', $(this));
        });
    },
    
    makeTabEditable : function(element) {
        //element.off('dblclick');
        element.off('hover');
        var oldName = $.trim(element.children('b').text());
        //console.log('oldName', oldName);
        element.children('b').hide();
        element.find('.button').hide();
        element.append('<input type="text" size="' + (oldName.length+4) + '" class="newName_" value="' + oldName + '">');

        var input = $('input', element);

        input.focus().select();

        input.on('blur', function() {
            if (debug) console.log('blur');
            var self = $(this);
            var newName = self.val();
            Command.setProperty(getId(element), "name", newName);
            _Resources.resetTab(element, newName);
        });
        
        input.keypress(function(e) {
            if (e.keyCode == 13 || e.keyCode == 9) {
                e.preventDefault(); 
                if (debug) console.log('keypress');
                var self = $(this);
                var newName = self.val();
                Command.setProperty(getId(element), "name", newName);
                _Resources.resetTab(element, newName);
            }
        });

        element.off('click');

    },

    appendResourceElement : function(entity, hasChildren) {

        if (debug) console.log('appendResourceElement', entity, hasChildren);

        resources.append('<div class="node resource ' + entity.id + '_"></div>');
        var div = $('.' + entity.id + '_', resources);

        entity.resourceId = entity.id;

        div.append('<img class="typeIcon" src="icon/page.png">'
            + '<b class="name_">' + entity.name + '</b> <span class="id">' + entity.id + '</span>');

        _Entities.appendExpandIcon(div, entity, hasChildren);

        div.append('<img title="Delete resource \'' + entity.name + '\'" alt="Delete resource \'' + entity.name + '\'" class="delete_icon button" src="' + Structr.delete_icon + '">');
        $('.delete_icon', div).on('click', function(e) {
            e.stopPropagation();
            //var self = $(this);
            //self.off('click');
            //self.off('mouseover');
            _Entities.deleteNode(this, entity);
        });

        div.append('<img title="Clone resource \'' + entity.name + '\'" alt="Clone resource \'' + entity.name + '\'" class="clone_icon button" src="' + _Resources.clone_icon + '">');
        $('.clone_icon', div).on('click', function(e) {
            e.stopPropagation();
            //var self = $(this);
            //self.off('click');
            //self.off('mouseover');
            Command.cloneResource(entity.id);
        });

        _Entities.appendEditPropertiesIcon(div, entity);
        _Entities.setMouseOver(div);

        var tab = _Resources.addTab(entity);

        previews.append('<div class="previewBox"><iframe id="preview_'
            + entity.id + '"></iframe></div><div style="clear: both"></div>');

        _Resources.resetTab(tab, entity.name);

        $('#preview_' + entity.id).hover(function() {
            var self = $(this);
            var elementContainer = self.contents().find('.structr-element-container');
            //console.log(self, elementContainer);
            elementContainer.addClass('structr-element-container-active');
            elementContainer.removeClass('structr-element-container');
        }, function() {
            var self = $(this);
            var elementContainer = self.contents().find('.structr-element-container-active');
            //console.log(elementContainer);
            elementContainer.addClass('structr-element-container');
            elementContainer.removeClass('structr-element-container-active');
        //self.find('.structr-element-container-header').remove();
        });

        $('#preview_' + entity.id).load(function() {

            var offset = $(this).offset();

            //console.log(this);
            var doc = $(this).contents();
            var head = $(doc).find('head');
            if (head) head.append('<style media="screen" type="text/css">'
                + '* { z-index: 0}\n'
                + '.nodeHover { border: 1px dotted red; }\n'
                + '.structr-content-container { display: inline-block; border: none; margin: 0; padding: 0; min-height: 10px; min-width: 10px; }\n'
                //		+ '.structr-element-container-active { display; inline-block; border: 1px dotted #e5e5e5; margin: -1px; padding: -1px; min-height: 10px; min-width: 10px; }\n'
                //		+ '.structr-element-container { }\n'
                + '.structr-element-container-active:hover { border: 1px dotted red ! important; }\n'
                + '.structr-droppable-area { border: 1px dotted red ! important; }\n'
                + '.structr-editable-area { border: 1px dotted orange ! important; }\n'
                + '.structr-editable-area-active { background-color: #ffe; border: 1px solid orange ! important; color: #333; margin: -1px; padding: 1px; }\n'
                //		+ '.structr-element-container-header { font-family: Arial, Helvetica, sans-serif ! important; position: absolute; font-size: 8pt; }\n'
                + '.structr-element-container-header { font-family: Arial, Helvetica, sans-serif ! important; position: absolute; font-size: 8pt; color: #333; border-radius: 5px; border: 1px solid #a5a5a5; padding: 3px 6px; margin: 6px 0 0 0; background-color: #eee; background: -webkit-gradient(linear, left bottom, left top, from(#ddd), to(#eee)) no-repeat; background: -moz-linear-gradient(90deg, #ddd, #eee) no-repeat; filter: progid:DXImageTransform.Microsoft.Gradient(StartColorStr="#eeeeee", EndColorStr="#dddddd", GradientType=0);\n'
                + '.structr-element-container-header img { width: 16px ! important; height: 16px ! important; }\n'
                + '.link-hover { border: 1px solid #00c; }\n'
                + '.edit_icon, .add_icon, .delete_icon, .close_icon, .key_icon {  cursor: pointer; heigth: 16px; width: 16px; vertical-align: top; float: right;  position: relative;}\n'
                + '</style>');
	
            var iframeDocument = $(this).contents();
            //var iframeWindow = this.contentWindow;

            var droppables = iframeDocument.find('[structr_element_id]');

            if (droppables.length == 0) {

                //iframeDocument.append('<html structr_element_id="' + entity.id + '">dummy element</html>');
                var html = iframeDocument.find('html');
                html.attr('structr_element_id', entity.id);
                html.addClass('structr-element-container');

            }
            droppables = iframeDocument.find('[structr_element_id]');

            droppables.each(function(i,element) {
                //console.log(element);
                var el = $(element);

                el.droppable({
                    accept: '.element, .content, .component',
                    greedy: true,
                    hoverClass: 'structr-droppable-area',
                    iframeOffset: {
                        'top' : offset.top,
                        'left' : offset.left
                    },
                    drop: function(event, ui) {
                        var self = $(this);
                        var resource = self.closest( '.resource')[0];
                        var resourceId;
                        var pos;
                        var nodeData = {};
    
                        if (resource) {

                            // we're in the main page
                            resourceId = getId(resource);
                            pos = $('.content, .element', self).length;

                        } else {
                            
                            // we're in the iframe
                            resource = self.closest('[structr_resource_id]')[0];
                            resourceId = $(resource).attr('structr_resource_id');
                            pos = $('[structr_element_id]', self).length;
                        }
                        
                        var contentId = getId(ui.draggable);
                        var elementId = getId(self);

                        if (!elementId) elementId = self.attr('structr_element_id');

                        if (!contentId) {
                            // create element on the fly
                            //var el = _Elements.addElement(null, 'element', null);
                            var tag = $(ui.draggable).text();
                            nodeData.type = tag.capitalize();
                        }
						

                        var relData = {};
                        
                        if (resourceId) {
                            relData.resourceId = resourceId;
                            relData[resourceId] = pos;
                        } else {
                            relData['*'] = pos;
                        }

                        nodeData.tag = (tag != 'content' ? tag : '');
                        nodeData.id = contentId;
                        if (debug) console.log(relData);
                        Command.createAndAdd(elementId, nodeData, relData);
                    }
                });

                var structrId = el.attr('structr_element_id');
                //var type = el.attr('structr_type');
                //  var name = el.attr('structr_name');
                var tag  = element.nodeName.toLowerCase();
                if (structrId) {

                    $('.move_icon', el).on('mousedown', function(e) {
                        e.stopPropagation();
                        var self = $(this);
                        var element = self.closest('[structr_element_id]');
                        //var element = self.children('.structr-node');
                        if (debug) console.log(element);
                        var entity = Structr.entity(structrId, element.attr('structr_element_id'));
                        entity.type = element.attr('structr_type');
                        entity.name = element.attr('structr_name');
                        if (debug) console.log('move', entity);
                        //var parentId = element.attr('structr_element_id');
                        self.parent().children('.structr-node').show();
                    });

                    $('b', el).on('click', function(e) {
                        e.stopPropagation();
                        var self = $(this);
                        var element = self.closest('[structr_element_id]');
                        var entity = Structr.entity(structrId, element.attr('structr_element_id'));
                        entity.type = element.attr('structr_type');
                        entity.name = element.attr('structr_name');
                        if (debug) console.log('edit', entity);
                        //var parentId = element.attr('structr_element_id');
                        if (debug) console.log(element);
                        Structr.dialog('Edit Properties of ' + entity.id, function() {
                            if (debug) console.log('save')
                        }, function() {
                            if (debug) console.log('cancelled')
                        });
                        _Entities.showProperties(this, entity, $('#dialogBox .dialogText'));
                    });

                    $('.delete_icon', el).on('click', function(e) {
                        e.stopPropagation();
                        var self = $(this);
                        var element = self.closest('[structr_element_id]');
                        var entity = Structr.entity(structrId, element.attr('structr_element_id'));
                        entity.type = element.attr('structr_type');
                        entity.name = element.attr('structr_name');
                        if (debug) console.log('delete', entity);
                        var parentId = element.attr('structr_element_id');

                        Command.removeSourceFromTarget(entity.id, parentId);
                        _Entities.deleteNode(this, entity);
                    });
                    var offsetTop = -30;
                    var offsetLeft = 0;
                    el.on({
                        mouseover: function(e) {
                            e.stopPropagation();
                            var self = $(this);
                            //self.off('click');

                            self.addClass('structr-element-container-active');

//                            self.parent().children('.structr-element-container-header').remove();
//
//                            self.append('<div class="structr-element-container-header">'
//                                + '<img class="typeIcon" src="/structr/'+ _Elements.icon + '">'
//                                + '<b class="name_">' + name + '</b> <span class="id">' + structrId + '</b>'
//                                + '<img class="delete_icon structr-container-button" title="Delete ' + structrId + '" alt="Delete ' + structrId + '" src="/structr/icon/delete.png">'
//                                + '<img class="edit_icon structr-container-button" title="Edit properties of ' + structrId + '" alt="Edit properties of ' + structrId + '" src="/structr/icon/application_view_detail.png">'
//                                + '<img class="move_icon structr-container-button" title="Move ' + structrId + '" alt="Move ' + structrId + '" src="/structr/icon/arrow_move.png">'
//                                + '</div>'
//                                );

                            var nodes = Structr.node(structrId);
                            nodes.parent().removeClass('nodeHover');
                            nodes.addClass('nodeHover');

                            var pos = self.position();
                            var header = self.children('.structr-element-container-header');
                            header.css({
                                position: "absolute",
                                top: pos.top + offsetTop + 'px',
                                left: pos.left + offsetLeft + 'px',
                                cursor: 'pointer'
                            }).show();
                            if (debug) console.log(header);
                        },
                        mouseout: function(e) {
                            e.stopPropagation();
                            var self = $(this);
                            self.removeClass('.structr-element-container');
                            var header = self.children('.structr-element-container-header');
                            header.remove();
                            var nodes = Structr.node(structrId);
                            nodes.removeClass('nodeHover');
                        }
                    });

                }
            });

            $(this).contents().find('[structr_content_id]').each(function(i,element) {
                if (debug) console.log(element);
                var el = $(element);
                var structrId = el.attr('structr_content_id');
                if (structrId) {
                    
                    el.on({
                        mouseover: function(e) {
                            e.stopPropagation();
                            var self = $(this);
                            self.addClass('structr-editable-area');
                            self.attr('contenteditable', true);
                            $('#hoverStatus').text('Editable content element: ' + self.attr('structr_content_id'));
                            var nodes = Structr.node(structrId);
                            nodes.parent().removeClass('nodeHover');
                            nodes.addClass('nodeHover');
                        },
                        mouseout: function(e) {
                            e.stopPropagation();
                            var self = $(this);
                            //swapFgBg(self);
                            self.removeClass('structr-editable-area');
                            //self.attr('contenteditable', false);
                            $('#hoverStatus').text('-- non-editable --');
                            var nodes = Structr.node(structrId);
                            nodes.removeClass('nodeHover');
                        },
                        click: function(e) {
                            e.stopPropagation();
                            var self = $(this);
                            self.removeClass('structr-editable-area');
                            self.addClass('structr-editable-area-active');

                            // Store old text in global var
                            textBeforeEditing = cleanText(self.contents());
                            if (debug) console.log("textBeforeEditing", textBeforeEditing);

                        },
                        blur: function(e) {
                            e.stopPropagation();
                            var self = $(this);
                            contentSourceId = self.attr('structr_content_id');
                            var text = cleanText(self.contents());
                            if (debug) console.log('blur contentSourceId: ' + contentSourceId);
                            //_Resources.updateContent(contentSourceId, textBeforeEditing, self.contents().first().text());
                            //Command.patch(contentSourceId, textBeforeEditing, self.contents().first().text());
                            Command.patch(contentSourceId, textBeforeEditing, text);
                            contentSourceId = null;
                            self.attr('contenteditable', false);
                            self.removeClass('structr-editable-area-active');
                            _Resources.reloadPreviews();
                        }
                    });
				
                }
            });

        });

        return div;
	
    },

    appendElementElement : function(entity, parentId, componentId, resourceId, removeExisting, hasChildren) {
        if (debug) console.log('_Resources.appendElementElement', entity, parentId, componentId, resourceId, removeExisting, hasChildren);
        
        var div;
        if (entity.type == 'Component') {
            div = _Components.appendComponentElement(entity, parentId, componentId, resourceId, removeExisting, hasChildren);
        } else {
            div = _Elements.appendElementElement(entity, parentId, componentId, resourceId, removeExisting, hasChildren);
        }
        
        if (!div) return false;

        if (debug) console.log('appendElementElement div', div);
        var pos = Structr.numberOfNodes($(div).parent())-1;
        if (debug) console.log('pos', entity.id, pos);

        if (parentId) {

            $('.delete_icon', div).replaceWith('<img title="Remove ' + entity.type + ' \'' + entity.name + '\' from resource ' + parentId + '" '
                + 'alt="Remove ' + entity.type + ' ' + entity.name + ' from ' + parentId + '" class="delete_icon button" src="icon/brick_delete.png">');
            $('.delete_icon', div).on('click', function(e) {
                e.stopPropagation();
                var self = $(this);
                self.off('click');
                self.off('mouseover');
                console.log('Command.removeSourceFromTarget',entity.id, parentId, componentId, resourceId, pos);
                Command.removeSourceFromTarget(entity.id, parentId, componentId, resourceId, pos);
            });
        }

        _Entities.setMouseOver(div);

        var resource = div.closest( '.resource')[0];
        if (!resource && resources) {
            div.draggable({
                revert: 'invalid',
                containment: '#resources',
                zIndex: 4,
                helper: 'clone',
                start: function(event, ui) {
                    $(this).draggable(disable);
                }
            });
        }

        var sorting = false;
        var obj = {};
        obj.command = 'SORT';

        div.sortable({
            sortable: '.node',
            containment: '#resources',
            start: function(event, ui) {
                sorting = true;
                var resourceId = getId(ui.item.closest('.resource')[0]);
                obj.id = resourceId;
            },
            update: function(event, ui) {
                if (debug) console.log('---------')
                if (debug) console.log(resourceId);
                var data = {};
                $(ui.item).parent().children('.node').each(function(i,v) {
                    var self = $(this);
                    if (debug) console.log(getId(self), i);
                    data[getId(self)] = i;
                    obj.data = data;
                    _Entities.resetMouseOverState(v);
                });
                sendObj(obj);
                sorting = false;
                _Resources.reloadPreviews();
            },
            stop: function(event, ui) {
                sorting = false;
                _Entities.resetMouseOverState(ui.item);
            }
        });

        div.droppable({
            accept: '.element, .content, .component, .image, .file',
            greedy: true,
            hoverClass: 'nodeHover',
            tolerance: 'pointer',
            drop: function(event, ui) {
                var self = $(this);

                console.log('dropped', event, ui.draggable);
                
                if (sorting) {
                    if (debug) console.log('sorting, no drop allowed');
                    return;
                }
                var nodeData = {};
                var resourceId;
                var relData = {};
				
                var resource = self.closest('.resource')[0];

                if (debug) console.log(resource);
                var contentId = getId(ui.draggable);
                var elementId = getId(self);
                
                if (debug) console.log('contentId', contentId);
                if (debug) console.log('elementId', elementId);

                if (contentId == elementId) {
                    console.log('drop on self not allowed');
                    return;
                }
                
                var tag, name;
                var cls = Structr.getClass($(ui.draggable));
                
                if (cls == 'image') {
                    contentId = undefined;
                    name = $(ui.draggable).find('.name_').text();
                    console.log('Image dropped, creating <img> node', name);
                    nodeData._html_src = name;
                    nodeData.name = name;
                    tag = 'img';
                } else if (cls == 'file') {
                    name = $(ui.draggable).find('.name_').text();
                    console.log('File dropped, creating <a> node', name);
                    nodeData._html_href = '${link.name}';
                    nodeData._html_title = '${link.name}';
                    nodeData.linkable_id = contentId;
                    nodeData.childContent = '${parent.link.name}';
                    tag = 'a';
                    contentId = undefined;
                } else {               
                    if (!contentId) {
                        tag = $(ui.draggable).text();
                        
                        if (tag == 'p' || tag == 'h1' || tag == 'h2' || tag == 'h3' || tag == 'h4' || tag == 'h5' || tag == 'h5' || tag == 'li' || tag == 'em' || tag == 'title') {
                            nodeData.childContent = 'New Content';
                        }
                        
                        
                    } else {
                        tag = cls;
                    }
                }
                
                if (debug) console.log($(ui.draggable));
                var pos = Structr.numberOfNodes(self);
                if (debug) console.log(pos);

                if (resource) {
                    resourceId = getId(resource);
                    relData.resourceId = resourceId;
                    relData[resourceId] = pos;
                } else {
                    relData['*'] = pos;
                }
				
                if (!isExpanded(elementId, null, resourceId)) {
                    _Entities.toggleElement(self.children('.expand_icon'));
                }

                var component = self.closest( '.component')[0];
                if (component) {
                    var componentId = getId(component);
                    relData.componentId = componentId;
                    relData[componentId] = pos;
                }

                nodeData.tag = (tag != 'content' ? tag : '');
                nodeData.type = tag.capitalize();
                nodeData.id = contentId;
                nodeData.targetResourceId = resourceId;

                var sourceResource = ui.draggable.closest('.resource')[0];
                if (sourceResource) {
                    var sourceResourceId = getId(sourceResource);
                    nodeData.sourceResourceId = sourceResourceId;
                }

                if (debug) console.log('drop event in appendElementElement', elementId, nodeData, relData);
                Command.createAndAdd(elementId, nodeData, relData);
            }
        });

        return div;
    },

//    appendComponentElement : function(entity, parentId, componentId, resourceId, removeExisting, hasChildren) {
//        if (debug) console.log('Resources.appendComponentElement');
//        var div = _Components.appendComponentElement(entity, parentId, componentId, resourceId, removeExisting, hasChildren);
//        //console.log(div);
//        if (!div) return false;
//
//        console.log('appendComponentElement div', div);
//        var pos = Structr.numberOfNodes($(div).parent())-1;
//        console.log('pos', entity.id, pos);
//
//        if (parentId) {
//
//            $('.delete_icon', div).replaceWith('<img title="Remove component \'' + entity.name + '\' from resource ' + parentId + '" '
//                + 'alt="Remove component ' + entity.name + ' from ' + parentId + '" class="delete_icon button" src="' + _Components.delete_icon + '">');
//            $('.delete_icon', div).on('click', function(e) {
//                e.stopPropagation();
//                Command.removeSourceFromTarget(entity.id, parentId, componentId, resourceId, pos);
//            });
//
//        }
//
//        var resource = div.closest( '.resource')[0];
//        if (!resource && resources) {
//            div.draggable({
//                revert: 'invalid',
//                containment: '#main',
//                zIndex: 1,
//                helper: 'clone'
//            });
//        } else {
//            div.draggable({
//                containment: 'body',
//                revert: 'invalid',
//                zIndex: 1,
//                helper: 'clone'
//            });
//        }
//	
//        var sorting = false;
//        var obj = {};
//        obj.command = 'SORT';
//
//        div.sortable({
//            sortable: '.node',
//            containment: 'parent',
//            start: function(event, ui) {
//                sorting = true;
//                var resourceId = getId(ui.item.closest('.resource')[0]);
//                obj.id = resourceId;
//            },
//            update: function(event, ui) {
//                if (debug) console.log('---------')
//                if (debug) console.log(resourceId);
//                var data = {};
//                $(ui.item).parent().children('.node').each(function(i,v) {
//                    var self = $(this);
//                    if (debug) console.log(getId(self), i);
//                    data[getId(self)] = i;
//                    obj.data = data;
//                });
//                sendObj(obj);
//                sorting = false;
//                _Resources.reloadPreviews();
//            },
//            stop: function(event, ui) {
//                sorting = false;
//            }
//        });
//
//        div.droppable({
//            accept: '.element, .content',
//            greedy: true,
//            hoverClass: 'nodeHover',
//            tolerance: 'pointer',
//            drop: function(event, ui) {
//
//                var self = $(this);
//
//                var node = $(self.closest('.node')[0]);
//
//                var resource = node.closest( '.resource')[0];
//
//                if (debug) console.log(resource);
//                var contentId = getId(ui.draggable);
//                if (!contentId) {
//                    var tag = $(ui.draggable).text();
//                }
//                var pos = Structr.numberOfNodes(node.parent())-1;
//                if (debug) console.log(pos);
//                var relData = {};
//                if (resource) {
//                    var resourceId = getId(resource);
//                    relData[resourceId] = pos;
//                    relData.resourceId = resourceId;
//                } else {
//                    relData['*'] = pos;
//                }
//
//                var component = self.closest( '.component')[0];
//                if (component) {
//                    var componentId = getId(component);
//                    relData[componentId] = pos;
//                    relData.componentId = componentId;
//                }
//
//                var nodeData = {};
//                if (!contentId) {
//                    nodeData.type = tag.capitalize();
//                    nodeData.tag = (tag != 'content' ? tag : '');
//                }
//
//                if (debug) console.log('Content or Element dropped on Component', getId(node), nodeData, relData);
//                Command.createAndAdd(getId(node), nodeData, relData);
//            }
//        });
//
//        return div;
//    },

    appendContentElement : function(content, parentId, componentId, resourceId) {
        if (debug) console.log('Resources.appendContentElement', content, parentId, componentId, resourceId);
		
        var div = _Contents.appendContentElement(content, parentId, componentId, resourceId);
        if (!div) return false;

        if (debug) console.log('appendContentElement div', div);
        var pos = Structr.numberOfNodes($(div).parent())-1;
        if (debug) console.log('pos', content.id, pos);

        //var div = Structr.node(content.id, parentId, componentId, resourceId, pos);

        if (parentId) {
            $('.delete_icon', div).replaceWith('<img title="Remove content \'' + content.name + '\' from resource ' + parentId + '" '
                + 'alt="Remove content ' + content.name + ' from element ' + parentId + '" class="delete_icon button" src="' + _Contents.delete_icon + '">');
            $('.delete_icon', div).on('click', function(e) {
                e.stopPropagation();
                var self = $(this);
                //self.off('click');
                //self.off('mouseover');
                if (debug) console.log('Command.removeSourceFromTarget', content.id, parentId, componentId, resourceId, pos);
                Command.removeSourceFromTarget(content.id, parentId, componentId, resourceId, pos)
            });
        }

        _Entities.setMouseOver(div);

        div.draggable({
            iframeFix: true,
            revert: 'invalid',
            containment: '#resources',
            zIndex: 1,
            helper: 'clone'
        });
        return div;
    },

    removeComponentFromResource : function(entityId, parentId, componentId, resourceId, pos) {
        if (debug) console.log('Resources.removeComponentFromResource');

        var resource = Structr.node(resourceId);
        var component = Structr.node(entityId, componentId, componentId, resourceId, pos);
        //component.remove();
        
        if (!Structr.containsNodes(resource)) {
            _Entities.removeExpandIcon(resource);
        }
        var numberOfComponents = $('.component', resource).size();
        if (debug) console.log(numberOfComponents);
        if (numberOfComponents == 0) {
            enable($('.delete_icon', resource)[0]);
        }
        Command.removeSourceFromTarget(entityId, parentId, componentId, resourceId, pos);

    },
    //
    //    removeElementFromResource : function(entityId, parentId, componentId, resourceId, pos) {
    //        if (debug) console.log('Resources.removeElementFromResource');
    //
    //        var resource = Structr.node(resourceId);
    //        var element = Structr.node(entityId, parentId, componentId, resourceId, pos);
    //        //element.remove();
    //
    //        if (!Structr.containsNodes(resource)) {
    //            _Entities.removeExpandIcon(resource);
    //        }
    //
    //        var numberOfElements = $('.element', resource).size();
    //        if (debug) console.log(numberOfElements);
    //        if (numberOfElements == 0) {
    //            enable($('.delete_icon', resource)[0]);
    //        }
    //        Command.removeSourceFromTarget(entityId, parentId, componentId, resourceId, pos);
    //
    //    },
    //
    //    removeContentFromElement : function(entityId, parentId, componentId, resourceId, pos) {
    //
    //        var element = Structr.node(parentId, null, componentId, resourceId, pos);
    //        var contentEl = Structr.node(entityId, parentId, componentId, resourceId, pos);
    //        //contentEl.remove();
    //
    //        if (!Structr.containsNodes(element)) {
    //            _Entities.removeExpandIcon(element);
    //        }
    //
    //        var numberOfContents = $('.element', element).size();
    //        if (debug) console.log(numberOfContents);
    //        if (numberOfContents == 0) {
    //            enable($('.delete_icon', element)[0]);
    //        }
    //        Command.removeSourceFromTarget(entityId, parentId, componentId, resourceId, pos);
    //
    //    },

    removeFrom : function(entityId, parentId, componentId, resourceId, pos) {
        console.log('Resources.removeFrom', entityId, parentId, componentId, resourceId, pos);

        //var parent = Structr.node(parentId, null, componentId, resourceId);
        var element = Structr.node(entityId, parentId, componentId, resourceId, pos);

        var parent = $(element).parent();

        console.log('parent', parent);
        
        element.remove();

        if (!Structr.containsNodes(parent)) {
            _Entities.removeExpandIcon(parent);
        //enable('.delete_icon', parent);
        }


    },

    showSubEntities : function(resourceId, entity) {
        var headers = {
            'X-StructrSessionToken' : token
        };
        var follow = followIds(resourceId, entity);
        $(follow).each(function(i, nodeId) {
            if (nodeId) {
                //            console.log(rootUrl + nodeId);
                $.ajax({
                    url: rootUrl + nodeId,
                    dataType: 'json',
                    contentType: 'application/json; charset=utf-8',
                    async: false,
                    headers: headers,
                    success: function(data) {
                        if (!data || data.length == 0 || !data.result) return;
                        var result = data.result;
                        //                    console.log(result);
                        _Resources.appendElement(result, entity, resourceId);
                        _Resources.showSubEntities(resourceId, result);
                    }
                });
            }
        });
    },

    //    addNode : function(button, type, entity, resourceId) {
    //        if (isDisabled(button)) return;
    //        disable(button);
    //        var pos = $('.' + resourceId + '_ .' + entity.id + '_ > div.nested').length;
    //        //    console.log('addNode(' + type + ', ' + entity.id + ', ' + entity.id + ', ' + pos + ')');
    //        var url = rootUrl + type;
    //        var headers = {
    //            'X-StructrSessionToken' : token
    //        };
    //        var resp = $.ajax({
    //            url: url,
    //            //async: false,
    //            type: 'POST',
    //            dataType: 'json',
    //            contentType: 'application/json; charset=utf-8',
    //            headers: headers,
    //            data: '{ "type" : "' + type + '", "name" : "' + type + '_' + Math.floor(Math.random() * (9999 - 1)) + '", "elements" : "' + entity.id + '" }',
    //            success: function(data) {
    //                var getUrl = resp.getResponseHeader('Location');
    //                $.ajax({
    //                    url: getUrl + '/all',
    //                    success: function(data) {
    //                        var node = data.result;
    //                        if (entity) {
    //                            _Resources.appendElement(node, entity, resourceId);
    //                            _Resources.setPosition(resourceId, getUrl, pos);
    //                        }
    //                        //disable($('.' + groupId + '_ .delete_icon')[0]);
    //                        enable(button);
    //                    }
    //                });
    //            }
    //        });
    //    },

    reloadPreviews : function() {

        $('iframe', $('#previews')).each(function() {
            var self = $(this);
            var resourceId = self.attr('id').substring('preview_'.length);
            var name = $(Structr.node(resourceId)[0]).children('b.name_').text();
            var doc = this.contentDocument;
            doc.location = name;
            doc.location.reload(true);
        });
    },
    
    zoomPreviews : function(value) {
        $('.previewBox', previews).each(function() {
            var val = value/100;
            var box = $(this);

            box.css('-moz-transform',    'scale(' + val + ')');
            box.css('-o-transform',      'scale(' + val + ')');
            box.css('-webkit-transform', 'scale(' + val + ')');

            var w = origWidth * val;
            var h = origHeight * val;

            box.width(w);
            box.height(h);

            $('iframe', box).width(w);
            $('iframe', box).height(h);

            if (debug) console.log("box,w,h", box, w, h);

        });

    }

};
